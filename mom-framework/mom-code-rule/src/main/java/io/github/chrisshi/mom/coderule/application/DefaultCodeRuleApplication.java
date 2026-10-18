package io.github.chrisshi.mom.coderule.application;

import io.github.chrisshi.mom.coderule.CodeRuleDefinition;
import io.github.chrisshi.mom.coderule.CodeRuleFailure;
import io.github.chrisshi.mom.coderule.CodeRulePageParams;
import io.github.chrisshi.mom.coderule.CodeRuleView;
import io.github.chrisshi.mom.coderule.field.CodeFieldRegistry;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentException;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentHandler;
import io.github.chrisshi.mom.coderule.segment.CodeSegmentRegistry;
import io.github.chrisshi.mom.coderule.application.CodeRuleApplication;
import io.github.chrisshi.mom.coderule.store.CodeRuleStore;
import io.github.chrisshi.mom.core.id.IdGenerator;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 公共编码规则生命周期用例。
 *
 * <p>每个写用例只操作当前 Owner 的本地数据库，并通过规则级版本控制并发更新。
 * 本类负责规则定义校验和状态流转，不负责 HTTP 协议包装或跨 Owner 数据访问。</p>
 */
public class DefaultCodeRuleApplication implements CodeRuleApplication {
    private final CodeRuleStore store;
    private final CodeSegmentRegistry handlers;
    private final CodeFieldRegistry fields;
    private final IdGenerator ids;

    public DefaultCodeRuleApplication(CodeRuleStore store, CodeSegmentRegistry handlers,
                                      CodeFieldRegistry fields, IdGenerator ids) {
        this.store = store; this.handlers = handlers; this.fields = fields; this.ids = ids;
    }

    /**
     * 在同一事务中创建规则身份和首个草稿修订。
     *
     * @param ruleCode 规则稳定编码
     * @param usageScenario 规则使用场景
     * @param description 规则说明，可为空
     * @param definition 编码段定义
     * @return 新建规则的草稿修订视图
     * @throws CodeRuleFailure 编码、定义或唯一约束不满足时抛出
     */
    @Transactional
    public CodeRuleView create(String ruleCode, String usageScenario, String description, CodeRuleDefinition definition) {
        if (ruleCode == null || !ruleCode.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) bad("rule_code_invalid");
        usageScenario = scenario(usageScenario); description = description(description);
        validate(definition, false);
        String ruleId = ids.nextId();
        String revisionId = ids.nextId();
        try {
            store.insertRule(ruleId, ruleCode, usageScenario, description);
            store.insertRevision(revisionId, ruleId, 1, "DRAFT", definition);
        } catch (DuplicateKeyException ex) { conflict("rule_code_conflict"); }
        return detail(revisionId);
    }

    /**
     * 按修订 ID 查询规则详情；修订 ID 是读写目标，不能用 ruleId 代替。
     *
     * @param revisionId 规则修订 ID
     * @return 规则及修订视图
     * @throws CodeRuleFailure 修订不存在时抛出 NOT_FOUND
     */
    @Transactional(readOnly = true)
    public CodeRuleView detail(String revisionId) {
        CodeRuleStore.Revision revision = requireRevision(revisionId);
        CodeRuleStore.Rule rule = store.rule(revision.ruleId());
        if (rule == null) notFound();
        return view(rule, revision);
    }

    /**
     * 分页查询当前 Owner 的规则修订。
     *
     * @param query 已校验的分页和筛选条件
     * @return 平台统一分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<CodeRuleView> search(PageQuery<CodeRulePageParams> query) { return store.search(query); }

    /**
     * 更新草稿修订的说明和定义。
     *
     * <p>只有草稿允许修改；规则级版本用于阻止不同修订或并发请求互相覆盖。</p>
     *
     * @param revisionId 待更新修订 ID
     * @param usageScenario 规则使用场景
     * @param description 规则说明，可为空
     * @param definition 新的编码段定义
     * @param ruleVersion 客户端读取到的规则级版本
     * @return 更新后的草稿视图
     * @throws CodeRuleFailure 修订状态、定义或版本不满足时抛出
     */
    @Transactional
    public CodeRuleView updateDraft(String revisionId, String usageScenario, String description, CodeRuleDefinition definition, long ruleVersion) {
        CodeRuleStore.Revision current = requireRevision(revisionId);
        CodeRuleStore.Rule rule = lock(current.ruleId(), ruleVersion);
        current = requireRevision(revisionId);
        if (!"DRAFT".equals(current.status())) conflict("immutable_revision");
        usageScenario = scenario(usageScenario); description = description(description);
        validate(definition, false);
        if (!store.updateMetadata(rule.id(), usageScenario, description) || !store.updateDraft(revisionId, definition) || !store.advanceVersion(rule.id(), ruleVersion)) conflict("stale_version");
        return detail(revisionId);
    }

    /**
     * 从已发布或停用修订复制定义，创建下一份草稿。
     *
     * @param revisionId 源修订 ID
     * @param ruleVersion 客户端读取到的规则级版本
     * @return 新建草稿视图
     * @throws CodeRuleFailure 源修订不可复制、已有草稿或版本冲突时抛出
     */
    @Transactional
    public CodeRuleView revise(String revisionId, long ruleVersion) {
        CodeRuleStore.Revision source = requireRevision(revisionId);
        CodeRuleStore.Rule rule = lock(source.ruleId(), ruleVersion);
        source = requireRevision(revisionId);
        if ("DRAFT".equals(source.status())) conflict("revision_is_draft");
        var revisions = store.revisions(rule.id());
        if (revisions.stream().anyMatch(r -> "DRAFT".equals(r.status()))) conflict("draft_exists");
        int next = revisions.stream().mapToInt(CodeRuleStore.Revision::revisionNo).max().orElse(0) + 1;
        String id = ids.nextId();
        store.insertRevision(id, rule.id(), next, "DRAFT", source.definition());
        if (!store.advanceVersion(rule.id(), ruleVersion)) conflict("stale_version");
        return detail(id);
    }

    /**
     * 重新校验当前能力并发布指定草稿，同时停用同一规则的旧发布版本。
     *
     * @param revisionId 待发布修订 ID
     * @param ruleVersion 客户端读取到的规则级版本
     * @return 发布后的规则视图
     * @throws CodeRuleFailure 定义无效、状态非法或版本冲突时抛出
     */
    @Transactional
    public CodeRuleView enable(String revisionId, long ruleVersion) {
        CodeRuleStore.Revision target = requireRevision(revisionId);
        CodeRuleStore.Rule rule = lock(target.ruleId(), ruleVersion);
        target = requireRevision(revisionId);
        if ("ENABLED".equals(target.status())) conflict("already_enabled");
        validate(target.definition(), true);
        for (CodeRuleStore.Revision revision : store.revisions(rule.id())) {
            if ("ENABLED".equals(revision.status()) && !store.changeStatus(revision.id(), "ENABLED", "DISABLED"))
                conflict("stale_version");
        }
        if (!store.changeStatus(revisionId, target.status(), "ENABLED")
                || !store.advanceVersion(rule.id(), ruleVersion)) conflict("stale_version");
        return detail(revisionId);
    }

    /**
     * 停用当前已发布修订，并使用规则级版本防止并发覆盖。
     *
     * @param revisionId 待停用修订 ID
     * @param ruleVersion 客户端读取到的规则级版本
     * @return 停用后的规则视图
     * @throws CodeRuleFailure 修订状态非法或版本冲突时抛出
     */
    @Transactional
    public CodeRuleView disable(String revisionId, long ruleVersion) {
        CodeRuleStore.Revision target = requireRevision(revisionId);
        CodeRuleStore.Rule rule = lock(target.ruleId(), ruleVersion);
        target = requireRevision(revisionId);
        if (!"ENABLED".equals(target.status())) conflict("not_enabled");
        if (!store.changeStatus(revisionId, "ENABLED", "DISABLED")
                || !store.advanceVersion(rule.id(), ruleVersion)) conflict("stale_version");
        return detail(revisionId);
    }

    private static String scenario(String value) {
        if (value == null || value.isBlank()) bad("usage_scenario_invalid");
        String text = value.strip();
        if (text.codePointCount(0, text.length()) > 120 || text.indexOf(0) >= 0) bad("usage_scenario_invalid");
        return text;
    }
    private static String description(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.strip();
        if (text.codePointCount(0, text.length()) > 1000 || text.indexOf(0) >= 0) bad("description_invalid");
        return text;
    }

    private void validate(CodeRuleDefinition definition, boolean complete) {
        if (definition == null || definition.schemaVersion() != 1 || definition.segments() == null) bad("definition_invalid");
        if (complete && definition.segments().isEmpty()) bad("definition_incomplete");
        Set<String> ids = new HashSet<>();
        int sequence = 0;
        for (CodeRuleDefinition.Segment segment : definition.segments()) {
            if (segment == null || segment.id() == null || !segment.id().matches("[A-Za-z][A-Za-z0-9_-]{0,63}")
                    || !ids.add(segment.id()) || segment.params() == null) bad("segment_invalid");
            CodeSegmentHandler handler;
            try { handler = handlers.require(segment.type()); }
            catch (CodeSegmentException ex) { bad("segment_unsupported"); return; }
            if ("SEQUENCE".equals(segment.type())) sequence++;
            Map<String, Object> params = segment.params();
            var schema = handler.metadata().parameterSchema();
            Set<String> allowed = new HashSet<>();
            for (var field : schema.fields()) allowed.add(field.name());
            if (!allowed.containsAll(params.keySet())) bad("parameter_unknown");
            for (var field : schema.fields()) {
                Object value = params.get(field.name());
                if (value == null || value instanceof String s && s.isBlank()) continue;
                switch (field.type()) {
                    case TEXT -> { if (!(value instanceof String)) bad("parameter_invalid"); }
                    case SELECT -> {
                        if (!(value instanceof String text) || field.options().stream().noneMatch(o -> o.value().equals(text)))
                            bad("parameter_invalid");
                    }
                    case NUMBER -> {
                        if (!(value instanceof Number)) bad("parameter_invalid");
                        java.math.BigDecimal decimal;
                        try { decimal = new java.math.BigDecimal(value.toString()); }
                        catch (NumberFormatException ex) { bad("parameter_invalid"); return; }
                        if (field.integer() && decimal.stripTrailingZeros().scale() > 0
                                || field.minimum() != null && decimal.compareTo(new java.math.BigDecimal(field.minimum().toString())) < 0
                                || field.maximum() != null && decimal.compareTo(new java.math.BigDecimal(field.maximum().toString())) > 0)
                            bad("parameter_invalid");
                    }
                }
            }
            if ("BUSINESS_FIELD".equals(segment.type())) {
                Object fieldKey = params.get("fieldKey");
                if (fieldKey != null && !(fieldKey instanceof String s && s.isBlank())) {
                    if (!(fieldKey instanceof String key)) bad("field_key_invalid");
                    try { fields.require((String) fieldKey); }
                    catch (CodeSegmentException ex) { bad("field_key_unsupported"); }
                }
            }
            boolean allPresent = schema.fields().stream().allMatch(f -> params.containsKey(f.name())
                    && params.get(f.name()) != null && !(params.get(f.name()) instanceof String s && s.isBlank()));
            if (complete && !allPresent) bad("parameter_missing");
            if (allPresent) {
                try { handler.validate(params); }
                catch (CodeSegmentException ex) { bad("parameter_invalid"); }
            }
        }
        if (sequence > 1 || complete && sequence != 1) bad("sequence_count_invalid");
    }

    private CodeRuleStore.Rule lock(String ruleId, long expected) {
        CodeRuleStore.Rule rule = store.lockRule(ruleId);
        if (rule == null) notFound();
        if (rule.version() != expected) conflict("stale_version");
        return rule;
    }
    private CodeRuleStore.Revision requireRevision(String id) {
        CodeRuleStore.Revision revision = store.revision(id);
        if (revision == null) notFound();
        return revision;
    }
    private static CodeRuleView view(CodeRuleStore.Rule rule, CodeRuleStore.Revision revision) {
        return new CodeRuleView(revision.id(), rule.id(), rule.ruleCode(), rule.usageScenario(), rule.description(), revision.revisionNo(),
                revision.status(), revision.definition(), rule.version(), revision.createdAt(), revision.updatedAt());
    }
    private static void bad(String code) { throw new CodeRuleFailure(CodeRuleFailure.Kind.BAD_REQUEST, code); }
    private static void conflict(String code) { throw new CodeRuleFailure(CodeRuleFailure.Kind.CONFLICT, code); }
    private static void notFound() { throw new CodeRuleFailure(CodeRuleFailure.Kind.NOT_FOUND, "revision_not_found"); }
}
