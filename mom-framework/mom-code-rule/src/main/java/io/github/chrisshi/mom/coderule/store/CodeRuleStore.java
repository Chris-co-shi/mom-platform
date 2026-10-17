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
    record Rule(String id, String ruleCode, long version) {
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

    void insertRule(String id, String ruleCode);

    void insertRevision(String id, String ruleId, int revisionNo, String status, CodeRuleDefinition definition);

    boolean updateDraft(String revisionId, CodeRuleDefinition definition);

    boolean changeStatus(String revisionId, String from, String to);

    boolean advanceVersion(String ruleId, long expected);

    PageResult<CodeRuleView> search(PageQuery<CodeRulePageParams> query);
}
