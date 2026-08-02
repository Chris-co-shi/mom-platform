package io.github.chrisshi.mom.iam.domain.authorization;

import io.github.chrisshi.mom.iam.domain.exception.IamDomainConflictException;
import io.github.chrisshi.mom.iam.domain.role.IamRole;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.domain.user.IamUserAccount;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * IAM 用户授权聚合。
 *
 * <p>角色、Factory Scope、Mobile Access 与 Party Binding 共享父 User Version，
 * 因此由一个领域对象统一判断关系替换与 Session 撤销决策。</p>
 */
public final class IamUserAuthorization {
    private final IamUserAccount user;
    private final IamPartyBinding partyBinding;

    public IamUserAuthorization(IamUserAccount user, IamPartyBinding partyBinding) {
        this.user = Objects.requireNonNull(user, "user");
        this.partyBinding = partyBinding;
    }

    public RoleReplacement replaceRoles(
            Set<String> roleIds, Collection<IamRole> selectedRoles, Long requestedVersion) {
        long expectedVersion = user.requireVersion(requestedVersion);
        for (IamRole role : selectedRoles) role.requireAssignableTo(user.userType());
        boolean retainsPlatformAdministrator = selectedRoles.stream()
                .anyMatch(IamRole::platformAdministrator);
        return new RoleReplacement(Set.copyOf(roleIds), expectedVersion, retainsPlatformAdministrator);
    }

    public FactoryScopeReplacement replaceFactoryScopes(
            Set<String> factoryIds, Long requestedVersion) {
        long expectedVersion = user.requireVersion(requestedVersion);
        if (user.external() && !factoryIds.isEmpty()) {
            if (partyBinding == null || !partyBinding.enabled()) {
                throw new IamDomainConflictException("外部用户缺少有效 Party Binding");
            }
        }
        return new FactoryScopeReplacement(Set.copyOf(factoryIds), expectedVersion);
    }

    public MobileAccessChange setMobileAccess(boolean enabled, Long requestedVersion) {
        long expectedVersion = user.requireVersion(requestedVersion);
        user.requireMobileAccessEligibility();
        return new MobileAccessChange(enabled, expectedVersion, !enabled);
    }

    public PartyRebind rebindParty(
            PartyType partyType, String partyId, Long requestedVersion) {
        long expectedVersion = user.requireVersion(requestedVersion);
        user.requirePartyBinding(partyType);
        return new PartyRebind(partyType, partyId, expectedVersion, true);
    }

    public UserType userType() {
        return user.userType();
    }

    public IamPartyBinding partyBinding() {
        return partyBinding;
    }

    public record RoleReplacement(
            Set<String> roleIds, long expectedVersion, boolean retainsPlatformAdministrator) { }

    public record FactoryScopeReplacement(Set<String> factoryIds, long expectedVersion) { }

    public record MobileAccessChange(
            boolean enabled, long expectedVersion, boolean revokeSessions) { }

    public record PartyRebind(
            PartyType partyType, String partyId, long expectedVersion, boolean revokeSessions) { }
}
