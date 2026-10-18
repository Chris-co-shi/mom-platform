package io.github.chrisshi.mom.coderule.application;

import io.github.chrisshi.mom.coderule.CodeRuleDefinition;
import io.github.chrisshi.mom.coderule.CodeRuleFailure;
import io.github.chrisshi.mom.coderule.CodeRulePageParams;
import io.github.chrisshi.mom.coderule.CodeRuleView;
import io.github.chrisshi.mom.core.page.PageQuery;
import io.github.chrisshi.mom.core.page.PageResult;

/**
 * Framework 公共编码规则用例接口。
 *
 * <p>实现由 Owner 绑定到各自的本地 DataSource。接口只描述规则生命周期和查询契约，
 * 不暴露 HTTP、数据库连接或跨 Owner 调用细节；所有写操作的并发保护由实现使用规则级版本完成。</p>
 */
public interface CodeRuleApplication {
    /**
     * 创建规则身份及首个草稿修订。
     *
     * @param ruleCode 规则稳定编码
     * @param usageScenario 规则使用场景，不能为空
     * @param description 规则说明，可为空
     * @param definition 编码段定义
     * @return 新建规则的草稿修订视图
     * @throws CodeRuleFailure 编码、定义或唯一约束不满足时抛出
     */
    CodeRuleView create(String ruleCode, String usageScenario, String description, CodeRuleDefinition definition);

    /**
     * 读取指定规则修订的完整视图。
     *
     * @param revisionId 规则修订 ID，不能使用规则身份 ID 替代
     * @return 规则及修订详情
     * @throws CodeRuleFailure 修订不存在时抛出
     */
    CodeRuleView detail(String revisionId);

    /**
     * 分页查询当前 Owner 的规则修订。
     *
     * @param query 分页和规则筛选条件
     * @return 平台统一分页结果
     */
    PageResult<CodeRuleView> search(PageQuery<CodeRulePageParams> query);

    /**
     * 在规则级版本保护下保存草稿说明和定义。
     *
     * @param revisionId 待更新草稿修订 ID
     * @param usageScenario 规则使用场景，不能为空
     * @param description 规则说明，可为空
     * @param definition 新的编码段定义
     * @param ruleVersion 客户端读取到的规则级版本
     * @return 更新后的草稿修订视图
     * @throws CodeRuleFailure 修订状态、定义或版本不满足时抛出
     */
    CodeRuleView updateDraft(String revisionId, String usageScenario, String description, CodeRuleDefinition definition, long ruleVersion);

    /**
     * 从已发布或停用修订复制下一份草稿。
     *
     * @param revisionId 源修订 ID
     * @param ruleVersion 客户端读取到的规则级版本
     * @return 新建草稿修订视图
     * @throws CodeRuleFailure 源状态非法、已有草稿或版本冲突时抛出
     */
    CodeRuleView revise(String revisionId, long ruleVersion);

    /**
     * 重新校验并发布指定修订，同时替换同一规则的旧发布版本。
     *
     * @param revisionId 待发布修订 ID
     * @param ruleVersion 客户端读取到的规则级版本
     * @return 发布后的修订视图
     * @throws CodeRuleFailure 定义无效、状态非法或版本冲突时抛出
     */
    CodeRuleView enable(String revisionId, long ruleVersion);

    /**
     * 停用指定的已发布修订。
     *
     * @param revisionId 待停用修订 ID
     * @param ruleVersion 客户端读取到的规则级版本
     * @return 停用后的修订视图
     * @throws CodeRuleFailure 状态非法或版本冲突时抛出
     */
    CodeRuleView disable(String revisionId, long ruleVersion);
}
