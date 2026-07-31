package io.github.chrisshi.mom.iam.domain.role;

import io.github.chrisshi.mom.iam.domain.exception.IamDomainConflictException;
import io.github.chrisshi.mom.iam.domain.exception.IamStaleVersionException;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.UserType;

import java.util.Objects;

/** IAM Role 聚合，承载可变性、状态和用户类型适用规则。 */
public record IamRole(
        String id,
        String code,
        String name,
        UserType applicableUserType,
        IamRecordStatus status,
        boolean builtIn,
        String description,
        long version) {

    public IamRole {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(applicableUserType, "applicableUserType");
        Objects.requireNonNull(status, "status");
        if (version < 0) throw new IllegalArgumentException("version 不能为负数");
    }

    public static IamRole create(
            String id, String code, String name, UserType type, String description) {
        return new IamRole(
                id, code, name, type, IamRecordStatus.ENABLED, false, description, 0L);
    }

    public long requireVersion(Long requested) {
        if (requested == null) throw new IllegalArgumentException("version 不能为空");
        if (requested.longValue() != version) {
            throw new IamStaleVersionException("version 已过期，请重新读取后重试");
        }
        return version;
    }

    public RoleChange change(
            String changedName,
            String changedDescription,
            IamRecordStatus changedStatus,
            Long requestedVersion) {
        requireMutable("内置角色在 P1.5 管理 API 中只读");
        long expectedVersion = requireVersion(requestedVersion);
        Objects.requireNonNull(changedStatus, "status 不能为空");
        return new RoleChange(changedName, changedDescription, changedStatus, expectedVersion);
    }

    public long preparePermissionReplacement(Long requestedVersion) {
        requireMutable("内置角色 Permission 关系由 Flyway 管理");
        return requireVersion(requestedVersion);
    }

    public void requireAssignableTo(UserType userType) {
        if (status != IamRecordStatus.ENABLED) {
            throw new IamDomainConflictException("禁用角色不能分配");
        }
        if (applicableUserType != userType) {
            throw new IamDomainConflictException("用户类型必须匹配角色 applicable_user_type");
        }
    }

    public void requirePlatformAdministratorInvariant() {
        if (!"PLATFORM_ADMIN".equals(code)
                || status != IamRecordStatus.ENABLED
                || applicableUserType != UserType.INTERNAL) {
            throw new IamDomainConflictException(
                    "内置 PLATFORM_ADMIN 角色必须保持 ENABLED 且适用于 INTERNAL");
        }
    }

    public boolean platformAdministrator() {
        return "PLATFORM_ADMIN".equals(code);
    }

    private void requireMutable(String message) {
        if (builtIn) throw new IamDomainConflictException(message);
    }

    public record RoleChange(
            String name,
            String description,
            IamRecordStatus status,
            long expectedVersion) { }
}
