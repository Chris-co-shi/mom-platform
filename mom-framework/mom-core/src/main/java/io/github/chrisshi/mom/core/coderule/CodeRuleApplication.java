package io.github.chrisshi.mom.core.coderule;

import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;

/**
 * Framework 公共规则用例接口；实现由 Owner 本地 DataSource 绑定。
 */
public interface CodeRuleApplication {
    CodeRuleView create(String ruleCode, CodeRuleDefinition definition);

    CodeRuleView detail(String revisionId);

    PageResult<CodeRuleView> search(PageQuery<CodeRulePageParams> query);

    CodeRuleView updateDraft(String revisionId, CodeRuleDefinition definition, long ruleVersion);

    CodeRuleView revise(String revisionId, long ruleVersion);

    CodeRuleView enable(String revisionId, long ruleVersion);

    CodeRuleView disable(String revisionId, long ruleVersion);
}
