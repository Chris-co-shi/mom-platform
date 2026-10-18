package io.github.chrisshi.mom.coderule.application;

import io.github.chrisshi.mom.coderule.CodeRuleDefinition;
import io.github.chrisshi.mom.coderule.CodeRulePageParams;
import io.github.chrisshi.mom.coderule.CodeRuleView;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;

/**
 * Framework 公共规则用例接口；实现由 Owner 本地 DataSource 绑定。
 */
public interface CodeRuleApplication {
    /** 创建规则级使用场景、说明及首个草稿；使用场景必填。 */
    CodeRuleView create(String ruleCode, String usageScenario, String description, CodeRuleDefinition definition);

    CodeRuleView detail(String revisionId);

    PageResult<CodeRuleView> search(PageQuery<CodeRulePageParams> query);

    /** 在现有规则级版本保护下同时保存规则说明和草稿定义。 */
    CodeRuleView updateDraft(String revisionId, String usageScenario, String description, CodeRuleDefinition definition, long ruleVersion);

    CodeRuleView revise(String revisionId, long ruleVersion);

    CodeRuleView enable(String revisionId, long ruleVersion);

    CodeRuleView disable(String revisionId, long ruleVersion);
}
