package io.github.chrisshi.mom.coderule.store;

import io.github.chrisshi.mom.coderule.CodeRulePageParams;
import io.github.chrisshi.mom.coderule.CodeRuleView;
import io.github.chrisshi.mom.coderule.CodeRuleDefinition;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;

import java.util.List;

/**
 * Owner 本地规则与修订的持久化契约。
 */
public interface CodeRuleStore {
    /** 规则身份、规则级共享说明和并发版本；旧规则的说明可为空。 */
    record Rule(String id, String ruleCode, String usageScenario, String description, long version) {
    }

    record Revision(String id, String ruleId, int revisionNo, String status,
                    CodeRuleDefinition definition, java.time.Instant createdAt, java.time.Instant updatedAt) {
    }

    /**
     * 按 ID 锁定规则身份行，调用者必须已开启本地事务。
     */
    Rule lockRule(String ruleId);

    Rule rule(String ruleId);

    Revision revision(String revisionId);

    List<Revision> revisions(String ruleId);

    /** 创建规则身份及规则级使用场景和说明。 */
    void insertRule(String id, String ruleCode, String usageScenario, String description);

    /** 在调用者已锁定规则行的事务中更新说明，版本与审计由 advanceVersion 同时推进。 */
    boolean updateMetadata(String ruleId, String usageScenario, String description);

    void insertRevision(String id, String ruleId, int revisionNo, String status, CodeRuleDefinition definition);

    boolean updateDraft(String revisionId, CodeRuleDefinition definition);

    boolean changeStatus(String revisionId, String from, String to);

    boolean advanceVersion(String ruleId, long expected);

    PageResult<CodeRuleView> search(PageQuery<CodeRulePageParams> query);
}
