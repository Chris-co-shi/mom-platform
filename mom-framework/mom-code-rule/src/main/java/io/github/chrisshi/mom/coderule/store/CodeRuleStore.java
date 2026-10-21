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

    /** 已提交的正式编码及请求指纹；同一请求键只能对应同一输入。 */
    record Issue(String ruleId, String requestKey, String requestHash, String code, String revisionId, int revisionNo) { }

    /**
     * 按 ID 锁定规则身份行，调用者必须已开启本地事务。
     */
    Rule lockRule(String ruleId);

    /** 按稳定规则编码锁定身份行，使发号与发布/停用串行化。 */
    Rule lockRuleByCode(String ruleCode);

    /** 按当前 Owner 内唯一的请求键查询结果；调用方已持有目标规则身份行锁。 */
    Issue issued(String requestKey);

    /** 原子分配作用域内下一流水；溢出时返回 null。 */
    Long nextSequence(String ruleId, String scopeKey, long maximum);

    /** 写入请求幂等与完整编码唯一记录，和流水分配处于同一本地事务。 */
    boolean insertIssue(Issue issue);

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
