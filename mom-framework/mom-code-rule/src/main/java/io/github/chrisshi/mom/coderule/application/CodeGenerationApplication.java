package io.github.chrisshi.mom.coderule.application;

import java.util.Map;

/** 当前 Owner 的正式编码生成契约；业务模块不得直接操作编码规则或流水表。 */
public interface CodeGenerationApplication {
    /**
     * 按稳定 ruleCode 分配正式编码。requestKey 在当前 Owner 内标识同一次申请，重试必须复用。
     *
     * @param ruleCode 当前 Owner 内的稳定规则编码
     * @param context 业务字段 Provider 所需的上下文标识
     * @param requestKey 调用方生成的稳定幂等标识
     * @return 已提交或本事务待提交的正式编码及发布修订
     */
    CodeGenerationResult generate(String ruleCode, Map<String, String> context, String requestKey);
}
