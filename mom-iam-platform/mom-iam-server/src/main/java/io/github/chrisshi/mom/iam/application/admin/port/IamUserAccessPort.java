package io.github.chrisshi.mom.iam.application.admin.port;

import io.github.chrisshi.mom.iam.domain.authorization.IamPartyBinding;
import io.github.chrisshi.mom.iam.domain.type.PartyType;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

/** Factory Scope、Mobile Access 与 Party Binding 写入 Port。 */
public interface IamUserAccessPort {
    void replaceFactoryScopes(
            String userId, Collection<String> factoryIds, String actor, Instant now,
            Supplier<String> idSupplier);
    void setMobileAccess(
            String userId, boolean enabled, String actor, Instant now,
            Supplier<String> idSupplier);
    Optional<IamPartyBinding> partyBinding(String userId);
    void rebindParty(
            String userId, PartyType partyType, String partyId, String actor,
            Instant now, Supplier<String> idSupplier);
}
