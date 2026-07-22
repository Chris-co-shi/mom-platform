package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.domain.type.PartyType;

import java.util.Set;

/**
 * 外部主体与 Factory 有效业务关系的权威校验端口。
 *
 * <p>Factory 主数据和供应商/客户业务关系不属于 IAM。S07 不复制这些数据，也不在缺少权威适配器时
 * 猜测关系；默认实现对非空外部 Factory Scope Fail Closed。后续由 MDM/业务关系服务适配该端口。</p>
 */
@FunctionalInterface
public interface IamExternalFactoryScopeVerifier {

    /**
     * @return 所有 factoryIds 是否都是该 Party 当前有效业务关系工厂的子集
     */
    boolean isAllowed(PartyType partyType, String partyId, Set<String> factoryIds);

    /** 缺少外部关系权威来源时的安全默认值。 */
    static IamExternalFactoryScopeVerifier failClosed() {
        return (partyType, partyId, factoryIds) -> factoryIds == null || factoryIds.isEmpty();
    }
}
