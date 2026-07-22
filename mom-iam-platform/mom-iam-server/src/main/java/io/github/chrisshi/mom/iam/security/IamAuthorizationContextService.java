package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamUserEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamAuthorizationContextRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamUserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Clock;
import java.time.Instant;

/** 从 IAM 权威数据计算 JWT 与 /api/iam/me 共用的当前有效授权上下文。 */
public final class IamAuthorizationContextService {
    private final IamUserRepository users;
    private final IamAuthorizationContextRepository contexts;
    private final Clock clock;

    public IamAuthorizationContextService(
            IamUserRepository users,
            IamAuthorizationContextRepository contexts,
            Clock clock) {
        this.users = users;
        this.contexts = contexts;
        this.clock = clock;
    }

    /** 按认证用户名加载授权上下文，供 Authorization Server 签发 Token。 */
    public IamAuthorizationContext loadByUsername(String username) {
        IamUserEntity user = users.findByUsername(normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException("账号不存在"));
        return calculate(user);
    }

    /** 按 JWT subject 用户 ID 加载授权上下文，供 /api/iam/me 和后续管理能力使用。 */
    public IamAuthorizationContext loadByUserId(String userId) {
        IamUserEntity user = users.findById(requireText(userId, "userId"))
                .orElseThrow(() -> new UsernameNotFoundException("账号不存在"));
        return calculate(user);
    }

    private IamAuthorizationContext calculate(IamUserEntity user) {
        Instant now = clock.instant();
        if (user.getStatus() != IamRecordStatus.ENABLED
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now))) {
            throw new AccessDeniedException("账号当前不可授权");
        }

        PartyType partyType = null;
        String partyId = null;
        if (user.getUserType() == UserType.SUPPLIER || user.getUserType() == UserType.CUSTOMER) {
            IamAuthorizationContextRepository.PartyScope party = contexts
                    .findEffectivePartyScope(user.getId(), now)
                    .orElseThrow(() -> new AccessDeniedException("外部账号缺少有效主体绑定"));
            PartyType expected = user.getUserType() == UserType.SUPPLIER
                    ? PartyType.SUPPLIER : PartyType.CUSTOMER;
            if (party.partyType() != expected) {
                throw new AccessDeniedException("外部账号主体类型不匹配");
            }
            partyType = party.partyType();
            partyId = party.partyId();
        }

        return new IamAuthorizationContext(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getUserType(),
                contexts.listEffectiveRoleCodes(user.getId(), user.getUserType(), now),
                contexts.listEffectivePermissionCodes(user.getId(), user.getUserType(), now),
                contexts.listEffectiveFactoryIds(user.getId(), now),
                partyType,
                partyId);
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }
}
