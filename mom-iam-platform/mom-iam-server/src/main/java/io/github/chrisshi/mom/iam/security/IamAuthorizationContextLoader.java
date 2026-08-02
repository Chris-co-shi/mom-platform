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

/**
 * 从 IAM 权威数据加载当前账号、Role、Permission、Factory 与 Party 的不可变授权快照。
 *
 * <p>该组件位于 IAM 安全应用边界，只依赖既有 Repository，不接收 Controller DTO、不签发 Token、
 * 不创建 Session，也不信任客户端 Claims。数据库不可用或账号状态不允许授权时异常向上传播并失败关闭。</p>
 */
public final class IamAuthorizationContextLoader {
    private final IamUserRepository users;
    private final IamAuthorizationContextRepository contexts;
    private final Clock clock;

    /** 创建权威授权上下文加载器。 */
    public IamAuthorizationContextLoader(
            IamUserRepository users,
            IamAuthorizationContextRepository contexts,
            Clock clock) {
        this.users = users;
        this.contexts = contexts;
        this.clock = clock;
    }

    /**
     * 按认证用户名加载当前有效授权上下文。
     *
     * @param username 已通过认证的用户名
     * @return IAM 权威授权快照
     */
    public IamAuthorizationContext loadByUsername(String username) {
        IamUserEntity user = users.findByUsername(normalize(username))
                .orElseThrow(() -> new UsernameNotFoundException("账号不存在"));
        return calculate(user);
    }

    /**
     * 按 JWT subject 用户 ID 重新加载当前有效授权上下文。
     *
     * @param userId IAM 技术主键
     * @return IAM 权威授权快照
     */
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
