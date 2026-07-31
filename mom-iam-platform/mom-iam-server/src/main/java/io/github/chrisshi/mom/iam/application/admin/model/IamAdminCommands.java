package io.github.chrisshi.mom.iam.application.admin.model;

import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;

import java.util.Set;

/** IAM Admin 已发布请求命令；字段名和类型属于 HTTP 兼容契约。 */
public final class IamAdminCommands {
    private IamAdminCommands() { }

    public record CreateUser(
            String username, String displayName, UserType userType, String initialPassword,
            PartyType partyType, String partyId) { }
    public record UpdateUser(String displayName, Long version) { }
    public record StatusChange(IamRecordStatus status, Long version, String reason) { }
    public record VersionedReason(Long version, String reason) { }
    public record PasswordReset(String temporaryPassword, Long version, String reason) { }
    public record RoleAssignment(Set<String> roleIds, Long version, String reason) { }
    public record FactoryScopeChange(Set<String> factoryIds, Long version, String reason) { }
    public record MobileAccessChange(boolean enabled, Long version, String reason) { }
    public record PartyRebind(PartyType partyType, String partyId, Long version, String reason) { }
    public record CreateRole(
            String code, String name, UserType applicableUserType, String description) { }
    public record UpdateRole(
            String name, String description, IamRecordStatus status, Long version, String reason) { }
    public record PermissionAssignment(Set<String> permissionIds, Long version, String reason) { }
    public record Reason(String reason) { }
    public record ClientStatusChange(IamRecordStatus status, Long version, String reason) { }
}
