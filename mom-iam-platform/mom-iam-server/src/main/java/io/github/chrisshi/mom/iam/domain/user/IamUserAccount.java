package io.github.chrisshi.mom.iam.domain.user;

import io.github.chrisshi.mom.iam.domain.exception.IamDomainConflictException;
import io.github.chrisshi.mom.iam.domain.exception.IamStaleVersionException;
import io.github.chrisshi.mom.iam.domain.model.IamDomainRules;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;

import java.time.Instant;
import java.util.Objects;

/**
 * IAM 登录账号聚合。
 *
 * <p>该聚合承载账号状态、并发版本、自操作保护、用户类型与 Party/Mobile 资格。
 * 密码摘要、Mapper、HTTP 和 Spring Security 不进入领域模型。</p>
 */
public record IamUserAccount(
        String id,
        String username,
        String displayName,
        UserType userType,
        IamRecordStatus status,
        int failedLoginCount,
        Instant lockedUntil,
        boolean passwordChangeRequired,
        boolean systemAccount,
        Instant lastLoginAt,
        long version) {

    public IamUserAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(userType, "userType");
        Objects.requireNonNull(status, "status");
        if (version < 0) throw new IllegalArgumentException("version 不能为负数");
    }

    public static IamUserAccount create(
            String id, String username, String displayName, UserType userType) {
        return new IamUserAccount(
                id, username, displayName, userType, IamRecordStatus.ENABLED,
                0, null, true, false, null, 0L);
    }

    /** 校验客户端读取版本，并返回持久化 CAS 使用的当前版本。 */
    public long requireVersion(Long requested) {
        if (requested == null) throw new IllegalArgumentException("version 不能为空");
        if (requested.longValue() != version) {
            throw new IamStaleVersionException("version 已过期，请重新读取后重试");
        }
        return version;
    }

    public DisplayNameChange changeDisplayName(String normalizedDisplayName, Long requestedVersion) {
        requireVersion(requestedVersion);
        return new DisplayNameChange(normalizedDisplayName, version);
    }

    public StatusChange changeStatus(
            String actorUserId, IamRecordStatus targetStatus, Long requestedVersion) {
        requireVersion(requestedVersion);
        Objects.requireNonNull(targetStatus, "status 不能为空");
        if (targetStatus == IamRecordStatus.DISABLED && id.equals(actorUserId)) {
            throw new IamDomainConflictException("不能禁用当前登录账号");
        }
        return new StatusChange(targetStatus, version, targetStatus == IamRecordStatus.DISABLED);
    }

    public VersionedAction unlock(Long requestedVersion) {
        requireVersion(requestedVersion);
        return new VersionedAction(version);
    }

    public VersionedAction resetCredential(String actorUserId, Long requestedVersion) {
        requireVersion(requestedVersion);
        if (id.equals(actorUserId)) {
            throw new IamDomainConflictException("管理员重置接口不能重置当前登录账号");
        }
        return new VersionedAction(version);
    }

    public VersionedAction delete(String actorUserId, Long requestedVersion) {
        requireVersion(requestedVersion);
        if (id.equals(actorUserId)) {
            throw new IamDomainConflictException("不能删除当前登录账号");
        }
        return new VersionedAction(version);
    }

    public void requirePartyBinding(PartyType partyType) {
        IamDomainRules.requireExternalBinding(userType, partyType);
    }

    public void requireMobileAccessEligibility() {
        if (userType != UserType.INTERNAL) {
            throw new IamDomainConflictException("Mobile Access 只允许 INTERNAL 用户");
        }
    }

    public boolean external() {
        return userType != UserType.INTERNAL;
    }

    public record DisplayNameChange(String displayName, long expectedVersion) { }
    public record StatusChange(
            IamRecordStatus status, long expectedVersion, boolean revokeSessions) { }
    public record VersionedAction(long expectedVersion) { }
}
