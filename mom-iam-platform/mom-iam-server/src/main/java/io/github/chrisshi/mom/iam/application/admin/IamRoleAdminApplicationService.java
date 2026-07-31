package io.github.chrisshi.mom.iam.application.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminActor;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminCommands;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminRequestContext;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.application.admin.port.IamAuthorizationAssignmentPort;
import io.github.chrisshi.mom.iam.application.admin.port.IamAuthorizationReadPort;
import io.github.chrisshi.mom.iam.application.admin.port.IamIdentifierGenerator;
import io.github.chrisshi.mom.iam.application.admin.port.IamPermissionAdminQueryPort;
import io.github.chrisshi.mom.iam.application.admin.port.IamRoleAdminQueryPort;
import io.github.chrisshi.mom.iam.domain.model.IamDomainRules;
import io.github.chrisshi.mom.iam.domain.role.IamRole;
import io.github.chrisshi.mom.iam.domain.role.IamRoleRepository;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** IAM Role 与 Permission 目录管理用例。 */
public class IamRoleAdminApplicationService {
    private final IamRoleRepository roles;
    private final IamRoleAdminQueryPort roleQueries;
    private final IamPermissionAdminQueryPort permissionQueries;
    private final IamAuthorizationAssignmentPort assignments;
    private final IamAuthorizationReadPort readModels;
    private final IamIdentifierGenerator ids;
    private final IamAdminAuditService audits;
    private final Clock clock;

    public IamRoleAdminApplicationService(
            IamRoleRepository roles,
            IamRoleAdminQueryPort roleQueries,
            IamPermissionAdminQueryPort permissionQueries,
            IamAuthorizationAssignmentPort assignments,
            IamAuthorizationReadPort readModels,
            IamIdentifierGenerator ids,
            IamAdminAuditService audits,
            Clock clock) {
        this.roles = roles;
        this.roleQueries = roleQueries;
        this.permissionQueries = permissionQueries;
        this.assignments = assignments;
        this.readModels = readModels;
        this.ids = ids;
        this.audits = audits;
        this.clock = clock;
    }

    public List<IamAdminViews.RoleView> listRoles(
            IamAdminActor actor, String userType, int limit, int offset) {
        actor.requirePermission("iam:role:read");
        return roleQueries.listRoles(
                userType,
                IamAdminCommandValidator.pageSize(limit),
                IamAdminCommandValidator.pageOffset(offset));
    }

    public IamAdminViews.RolePermissionView getRolePermissions(
            IamAdminActor actor, String roleId) {
        actor.requirePermission("iam:role:read");
        return requireRolePermissions(roleId);
    }

    @Transactional
    public IamAdminViews.RoleView createRole(
            IamAdminActor actor,
            IamAdminCommands.CreateRole command,
            IamAdminRequestContext request) {
        actor.requirePermission("iam:role:create");
        String roleId = ids.nextId();
        String code = IamDomainRules.requireBusinessCode(command.code(), "roleCode");
        String name = IamAdminCommandValidator.requireText(command.name(), "name", 200);
        String description = IamAdminCommandValidator.optionalText(command.description(), 1000);
        UserType type = Objects.requireNonNull(
                command.applicableUserType(), "applicableUserType 不能为空");
        IamRole role = IamRole.create(roleId, code, name, type, description);
        roles.create(role, actor.userId(), clock.instant());
        audits.record(
                actor, request, "iam.role.created", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.MEDIUM, "ROLE", roleId, null,
                "role_created", null, IamAdminCommandValidator.json("code", code));
        return requireRoleView(roleId);
    }

    @Transactional
    public IamAdminViews.RoleView updateRole(
            IamAdminActor actor,
            String roleId,
            IamAdminCommands.UpdateRole command,
            IamAdminRequestContext request) {
        actor.requirePermission("iam:role:update");
        IamRole role = lockRole(roleId);
        var decision = IamAdminFailures.fromDomain(() -> role.change(
                IamAdminCommandValidator.requireText(command.name(), "name", 200),
                IamAdminCommandValidator.optionalText(command.description(), 1000),
                Objects.requireNonNull(command.status(), "status 不能为空"),
                command.version()));
        IamAdminFailures.fromDomain(() -> roles.update(
                role.id(), decision.name(), decision.description(), decision.status(),
                decision.expectedVersion(), actor.userId(), clock.instant()));
        audits.record(
                actor, request, "iam.role.updated", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.MEDIUM, "ROLE", role.id(), null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"),
                null, IamAdminCommandValidator.json("status", command.status().name()));
        return requireRoleView(role.id());
    }

    @Transactional
    public IamAdminViews.RolePermissionView replaceRolePermissions(
            IamAdminActor actor,
            String roleId,
            IamAdminCommands.PermissionAssignment command,
            IamAdminRequestContext request) {
        actor.requirePermission("iam:role:permission-manage");
        IamRole role = lockRole(roleId);
        long expectedVersion = IamAdminFailures.fromDomain(() ->
                role.preparePermissionReplacement(command.version()));
        Set<String> permissionIds = IamAdminCommandValidator.normalizedIds(
                command.permissionIds(), "permissionIds");
        if (permissionQueries.findEnabledPermissionIds(permissionIds).size()
                != permissionIds.size()) {
            throw new IamAdminExceptions.NotFound("存在无效或禁用 Permission");
        }
        Instant now = clock.instant();
        assignments.replaceRolePermissions(
                role.id(), permissionIds, actor.userId(), now, ids::nextId);
        IamAdminFailures.fromDomain(() -> assignments.advanceRoleVersion(
                role.id(), expectedVersion, actor.userId(), now));
        audits.record(
                actor, request, "iam.role.permissions-replaced",
                SecurityEventCategory.AUTHORIZATION, PermissionRiskLevel.HIGH,
                "ROLE", role.id(), null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"),
                null, IamAdminCommandValidator.json(
                        "permissionCount", Integer.toString(permissionIds.size())));
        return requireRolePermissions(role.id());
    }

    public List<IamAdminViews.PermissionView> listPermissions(
            IamAdminActor actor, String domainCode, int limit, int offset) {
        actor.requirePermission("iam:permission:read");
        return permissionQueries.listPermissions(
                domainCode,
                IamAdminCommandValidator.pageSize(limit),
                IamAdminCommandValidator.pageOffset(offset));
    }

    private IamRole lockRole(String roleId) {
        return roles.lockById(IamAdminCommandValidator.requireId(roleId, "roleId"))
                .orElseThrow(() -> new IamAdminExceptions.NotFound("角色不存在"));
    }

    private IamAdminViews.RoleView requireRoleView(String roleId) {
        return roleQueries.findRole(roleId)
                .orElseThrow(() -> new IamAdminExceptions.NotFound("角色不存在"));
    }

    private IamAdminViews.RolePermissionView requireRolePermissions(String roleId) {
        return readModels.rolePermissions(
                        IamAdminCommandValidator.requireId(roleId, "roleId"))
                .orElseThrow(() -> new IamAdminExceptions.NotFound("角色不存在"));
    }
}
