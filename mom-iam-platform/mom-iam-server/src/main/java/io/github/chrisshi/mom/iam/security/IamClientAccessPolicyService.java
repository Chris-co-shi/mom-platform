package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.iam.domain.type.ApplicationCode;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamExternalUserBindingEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamOauthClientPolicyEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserApplicationEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamAuthorizationCatalogRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamIdentityBindingRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamUserAccessRepository;

import java.time.Clock;
import java.time.Instant;

/** 授权端点签发 Code 前执行 Client、user_type、Party Binding 与 Mobile Access 校验。 */
public final class IamClientAccessPolicyService {
    private final IamAccountAuthenticationService accounts;
    private final IamAuthorizationCatalogRepository catalog;
    private final IamIdentityBindingRepository bindings;
    private final IamUserAccessRepository accessRepository;
    private final Clock clock;

    public IamClientAccessPolicyService(
            IamAccountAuthenticationService accounts,
            IamAuthorizationCatalogRepository catalog,
            IamIdentityBindingRepository bindings,
            IamUserAccessRepository accessRepository,
            Clock clock) {
        this.accounts = accounts;
        this.catalog = catalog;
        this.bindings = bindings;
        this.accessRepository = accessRepository;
        this.clock = clock;
    }

    /** 不满足任何入口条件时统一拒绝，不向浏览器暴露具体账号或主体状态。 */
    public void requireAuthorization(String username, String clientId) {
        Instant now = clock.instant();
        IamUserEntity user = accounts.requireUser(username);
        if (user.getStatus() != IamRecordStatus.ENABLED
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now))) {
            throw new AccessDeniedException();
        }
        IamOauthClientPolicyEntity policy = catalog.findClientPolicyByClientId(clientId)
                .orElseThrow(AccessDeniedException::new);
        if (policy.getStatus() != IamRecordStatus.ENABLED
                || policy.getAllowedUserType() != user.getUserType()) {
            throw new AccessDeniedException();
        }
        if (user.getUserType() == UserType.SUPPLIER || user.getUserType() == UserType.CUSTOMER) {
            requireExternalBinding(user, now);
        }
        if (Boolean.TRUE.equals(policy.getMobileAccessRequired())) {
            requireMobileAccess(user, now);
        }
    }

    private void requireExternalBinding(IamUserEntity user, Instant now) {
        IamExternalUserBindingEntity binding = bindings.findExternalBindingByUserId(user.getId())
                .orElseThrow(AccessDeniedException::new);
        PartyType expected = user.getUserType() == UserType.SUPPLIER
                ? PartyType.SUPPLIER : PartyType.CUSTOMER;
        if (binding.getStatus() != IamRecordStatus.ENABLED
                || binding.getPartyType() != expected
                || !isActive(binding.getValidFrom(), binding.getValidUntil(), now)) {
            throw new AccessDeniedException();
        }
    }

    private void requireMobileAccess(IamUserEntity user, Instant now) {
        if (user.getUserType() != UserType.INTERNAL) {
            throw new AccessDeniedException();
        }
        IamUserApplicationEntity access = accessRepository.findApplicationAccess(
                        user.getId(), ApplicationCode.MOM_MOBILE_PDA)
                .orElseThrow(AccessDeniedException::new);
        if (access.getStatus() != IamRecordStatus.ENABLED
                || !isActive(access.getValidFrom(), access.getValidUntil(), now)) {
            throw new AccessDeniedException();
        }
    }

    private static boolean isActive(Instant validFrom, Instant validUntil, Instant now) {
        return (validFrom == null || !now.isBefore(validFrom))
                && (validUntil == null || now.isBefore(validUntil));
    }

    /** 入口策略统一拒绝异常，不携带具体安全状态。 */
    public static final class AccessDeniedException extends RuntimeException {
        public AccessDeniedException() {
            super("当前账号不能访问该应用");
        }
    }
}
