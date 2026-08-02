package io.github.chrisshi.mom.iam.application.admin.port;

import io.github.chrisshi.mom.iam.domain.type.PartyType;

import java.util.Set;

/** 外部 Party 与 Factory 有效业务关系的权威校验 Port。 */
@FunctionalInterface
public interface IamExternalFactoryScopeVerifier {
    boolean isAllowed(PartyType partyType, String partyId, Set<String> factoryIds);

    static IamExternalFactoryScopeVerifier failClosed() {
        return (partyType, partyId, factoryIds) -> factoryIds == null || factoryIds.isEmpty();
    }
}
