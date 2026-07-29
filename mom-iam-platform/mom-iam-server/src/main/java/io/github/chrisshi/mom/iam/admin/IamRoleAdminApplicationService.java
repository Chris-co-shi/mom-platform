package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.model.IamDomainRules;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamAuthorizationAssignmentRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamRoleAdminRepository;
import io.github.chrisshi.mom.security.token.MomJwtAuthorization;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * IAM 角色与 Permission 目录管理应用服务。
 *
 * <p>该服务保留角色行锁、客户端版本校验、内置角色只读、Permission 全量替换、
 * 父版本推进和成功审计的原顺序。写入公开方法持有本地事务，不依赖 Web、
 * Mapper 或 OAuth2 Adapter；持久化失败向上传播并整体回滚。</p>
 */
public class IamRoleAdminApplicationService {
    private final IamRoleAdminRepository roles;
    private final IamAuthorizationAssignmentRepository assignments;
    private final IamAdminReadModelRepository readModels;
    private final IamAdminOperationSupport support;

    IamRoleAdminApplicationService(
            IamRoleAdminRepository roles,
            IamAuthorizationAssignmentRepository assignments,
            IamAdminReadModelRepository readModels,
            IamAdminOperationSupport support) {
        this.roles = roles;
        this.assignments = assignments;
        this.readModels = readModels;
        this.support = support;
    }

    /** 按用户类型与分页语义读取角色目录。 */
    public List<IamAdminViews.RoleView> listRoles(
            Authentication authentication, String userType, int limit, int offset) {
        support.requirePermission(authentication, "iam:role:read");
        return roles.listRoles(userType,
                IamAdminCommandValidator.pageSize(limit), IamAdminCommandValidator.pageOffset(offset));
    }

    /** 读取含父角色版本的 Permission 快照。 */
    public IamAdminViews.RolePermissionView getRolePermissions(
            Authentication authentication, String roleId) {
        support.requirePermission(authentication, "iam:role:read");
        return readModels.rolePermissions(IamAdminCommandValidator.requireId(roleId, "roleId"));
    }

    /** 创建非内置角色并追加成功审计。 */
    @Transactional
    public IamAdminViews.RoleView createRole(
            Authentication authentication,
            IamAdminService.CreateRole command,
            IamAdminService.RequestContext request) {
        MomJwtAuthorization actor = support.actor(authentication, "iam:role:create");
        String roleId = support.nextId();
        String code = IamDomainRules.requireBusinessCode(command.code(), "roleCode");
        String name = IamAdminCommandValidator.requireText(command.name(), "name", 200);
        String description = IamAdminCommandValidator.optionalText(command.description(), 1000);
        UserType type = Objects.requireNonNull(command.applicableUserType(), "applicableUserType 不能为空");
        roles.insertRole(roleId, code, name, type, description, actor.userId(), support.now());
        support.audit(actor, request, "iam.role.created", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.MEDIUM, "ROLE", roleId, null,
                "role_created", null, IamAdminCommandValidator.json("code", code));
        return lockRole(roleId);
    }

    /** 持有角色行锁按版本更新非内置角色。 */
    @Transactional
    public IamAdminViews.RoleView updateRole(
            Authentication authentication, String roleId,
            IamAdminService.UpdateRole command, IamAdminService.RequestContext request) {
        MomJwtAuthorization actor = support.actor(authentication, "iam:role:update");
        IamAdminViews.RoleView role = lockRole(roleId);
        if (role.builtIn()) throw new IamAdminExceptions.Conflict("内置角色在 P1.5 管理 API 中只读");
        roles.updateRole(roleId,
                IamAdminCommandValidator.requireText(command.name(), "name", 200),
                IamAdminCommandValidator.optionalText(command.description(), 1000),
                Objects.requireNonNull(command.status(), "status 不能为空"),
                IamAdminCommandValidator.requireVersion(command.version(), role.version()),
                actor.userId(), support.now());
        support.audit(actor, request, "iam.role.updated", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.MEDIUM, "ROLE", roleId, null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"), null,
                IamAdminCommandValidator.json("status", command.status().name()));
        return lockRole(roleId);
    }

    /** 按角色聚合版本全量替换非内置角色 Permission。 */
    @Transactional
    public IamAdminViews.RolePermissionView replaceRolePermissions(
            Authentication authentication, String roleId,
            IamAdminService.PermissionAssignment command, IamAdminService.RequestContext request) {
        MomJwtAuthorization actor = support.actor(authentication, "iam:role:permission-manage");
        IamAdminViews.RoleView role = lockRole(roleId);
        long version = IamAdminCommandValidator.requireVersion(command.version(), role.version());
        if (role.builtIn()) throw new IamAdminExceptions.Conflict("内置角色 Permission 关系由 Flyway 管理");
        Set<String> permissionIds = IamAdminCommandValidator.normalizedIds(
                command.permissionIds(), "permissionIds");
        if (roles.findEnabledPermissionIds(permissionIds).size() != permissionIds.size()) {
            throw new IamAdminExceptions.NotFound("存在无效或禁用 Permission");
        }
        Instant now = support.now();
        assignments.replaceRolePermissions(roleId, permissionIds, actor.userId(), now, support::nextId);
        assignments.advanceRoleVersion(roleId, version, actor.userId(), now);
        support.audit(actor, request, "iam.role.permissions-replaced", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.HIGH, "ROLE", roleId, null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"), null,
                IamAdminCommandValidator.json("permissionCount", Integer.toString(permissionIds.size())));
        return readModels.rolePermissions(roleId);
    }

    /** 按领域和分页语义读取只读 Permission 目录。 */
    public List<IamAdminViews.PermissionView> listPermissions(
            Authentication authentication, String domainCode, int limit, int offset) {
        support.requirePermission(authentication, "iam:permission:read");
        return roles.listPermissions(domainCode,
                IamAdminCommandValidator.pageSize(limit), IamAdminCommandValidator.pageOffset(offset));
    }

    private IamAdminViews.RoleView lockRole(String roleId) {
        return roles.lockRole(IamAdminCommandValidator.requireId(roleId, "roleId"))
                .orElseThrow(() -> new IamAdminExceptions.NotFound("角色不存在"));
    }
}
