package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Set;

/**
 * IAM Admin 已发布 Controller 的兼容应用 Facade。
 *
 * <p>该 Facade 保留 Controller 依赖、公开方法签名与 JSON 命令 record，只把调用委托给
 * 用户、用户授权、角色、Session、Client 和审计查询服务。事务不在 Facade 上重复
 * 开启，而是位于每个内聚 Application Service 的公开写方法，因此锁、版本、Session 撤销
 * 和审计仍在原单一事务中。Facade 不访问 Repository、Token 或外部基础设施。</p>
 */
public final class IamAdminService {
    private final IamUserAdminApplicationService users;
    private final IamUserAuthorizationApplicationService userAuthorizations;
    private final IamRoleAdminApplicationService roles;
    private final IamSessionAdminApplicationService sessions;
    private final IamClientAdminApplicationService clients;
    private final IamSecurityAuditQueryService audits;

    /** 创建仅负责委托的兼容 Facade。 */
    IamAdminService(
            IamUserAdminApplicationService users,
            IamUserAuthorizationApplicationService userAuthorizations,
            IamRoleAdminApplicationService roles,
            IamSessionAdminApplicationService sessions,
            IamClientAdminApplicationService clients,
            IamSecurityAuditQueryService audits) {
        this.users = users;
        this.userAuthorizations = userAuthorizations;
        this.roles = roles;
        this.sessions = sessions;
        this.clients = clients;
        this.audits = audits;
    }

    public List<IamAdminViews.UserView> listUsers(
            Authentication authentication, String userType, String status, int limit, int offset) {
        return users.listUsers(authentication, userType, status, limit, offset);
    }

    public IamAdminViews.UserView getUser(Authentication authentication, String userId) {
        return users.getUser(authentication, userId);
    }

    public IamAdminViews.UserAuthorizationView getUserAuthorization(
            Authentication authentication, String userId) {
        return userAuthorizations.getUserAuthorization(authentication, userId);
    }

    public IamAdminViews.UserView createUser(
            Authentication authentication, CreateUser command, RequestContext request) {
        return users.createUser(authentication, command, request);
    }

    public IamAdminViews.UserView updateUser(
            Authentication authentication, String userId, UpdateUser command, RequestContext request) {
        return users.updateUser(authentication, userId, command, request);
    }

    public IamAdminViews.UserView setUserStatus(
            Authentication authentication, String userId, StatusChange command, RequestContext request) {
        return users.setUserStatus(authentication, userId, command, request);
    }

    public IamAdminViews.UserView unlockUser(
            Authentication authentication, String userId, VersionedReason command, RequestContext request) {
        return users.unlockUser(authentication, userId, command, request);
    }

    public IamAdminViews.UserView resetPassword(
            Authentication authentication, String userId, PasswordReset command, RequestContext request) {
        return users.resetPassword(authentication, userId, command, request);
    }

    public void deleteUser(
            Authentication authentication, String userId, VersionedReason command, RequestContext request) {
        users.deleteUser(authentication, userId, command, request);
    }

    public IamAdminViews.UserAuthorizationView replaceUserRoles(
            Authentication authentication, String userId, RoleAssignment command, RequestContext request) {
        return userAuthorizations.replaceUserRoles(authentication, userId, command, request);
    }

    public IamAdminViews.UserAuthorizationView replaceFactoryScopes(
            Authentication authentication, String userId, FactoryScopeChange command, RequestContext request) {
        return userAuthorizations.replaceFactoryScopes(authentication, userId, command, request);
    }

    public IamAdminViews.UserAuthorizationView setMobileAccess(
            Authentication authentication, String userId, MobileAccessChange command, RequestContext request) {
        return userAuthorizations.setMobileAccess(authentication, userId, command, request);
    }

    public IamAdminViews.UserAuthorizationView rebindParty(
            Authentication authentication, String userId, PartyRebind command, RequestContext request) {
        return userAuthorizations.rebindParty(authentication, userId, command, request);
    }

    public List<IamAdminViews.RoleView> listRoles(
            Authentication authentication, String userType, int limit, int offset) {
        return roles.listRoles(authentication, userType, limit, offset);
    }

    public IamAdminViews.RolePermissionView getRolePermissions(
            Authentication authentication, String roleId) {
        return roles.getRolePermissions(authentication, roleId);
    }

    public IamAdminViews.RoleView createRole(
            Authentication authentication, CreateRole command, RequestContext request) {
        return roles.createRole(authentication, command, request);
    }

    public IamAdminViews.RoleView updateRole(
            Authentication authentication, String roleId, UpdateRole command, RequestContext request) {
        return roles.updateRole(authentication, roleId, command, request);
    }

    public IamAdminViews.RolePermissionView replaceRolePermissions(
            Authentication authentication, String roleId,
            PermissionAssignment command, RequestContext request) {
        return roles.replaceRolePermissions(authentication, roleId, command, request);
    }

    public List<IamAdminViews.PermissionView> listPermissions(
            Authentication authentication, String domainCode, int limit, int offset) {
        return roles.listPermissions(authentication, domainCode, limit, offset);
    }

    public List<IamAdminViews.SessionView> listSessions(
            Authentication authentication, String userId, String status, int limit, int offset) {
        return sessions.listSessions(authentication, userId, status, limit, offset);
    }

    public void revokeSession(
            Authentication authentication, String sessionId, Reason command, RequestContext request) {
        sessions.revokeSession(authentication, sessionId, command, request);
    }

    public int revokeAllSessions(
            Authentication authentication, String userId, Reason command, RequestContext request) {
        return sessions.revokeAllSessions(authentication, userId, command, request);
    }

    public List<IamAdminViews.SecurityAuditView> listAudit(
            Authentication authentication, String category, String targetId, int limit, int offset) {
        return audits.listAudit(authentication, category, targetId, limit, offset);
    }

    public List<IamAdminViews.ClientView> listClients(Authentication authentication) {
        return clients.listClients(authentication);
    }

    public IamAdminViews.ClientView setClientStatus(
            Authentication authentication, String clientId,
            ClientStatusChange command, RequestContext request) {
        return clients.setClientStatus(authentication, clientId, command, request);
    }

    public record RequestContext(String ipAddress, String userAgent) { }
    public record CreateUser(
            String username, String displayName, UserType userType, String initialPassword,
            PartyType partyType, String partyId) { }
    public record UpdateUser(String displayName, Long version) { }
    public record StatusChange(IamRecordStatus status, Long version, String reason) { }
    public record VersionedReason(Long version, String reason) { }
    public record PasswordReset(String temporaryPassword, Long version, String reason) { }
    /** 用户角色全量替换命令；version 必须来自最近一次用户授权读取。 */
    public record RoleAssignment(Set<String> roleIds, Long version, String reason) { }
    /** 用户 Factory Scope 全量替换命令；version 必须来自最近一次用户授权读取。 */
    public record FactoryScopeChange(Set<String> factoryIds, Long version, String reason) { }
    /** 用户移动端访问变更命令；version 必须来自最近一次用户授权读取。 */
    public record MobileAccessChange(boolean enabled, Long version, String reason) { }
    /** 外部用户 Party 重绑命令；version 必须来自最近一次用户授权读取。 */
    public record PartyRebind(PartyType partyType, String partyId, Long version, String reason) { }
    public record CreateRole(String code, String name, UserType applicableUserType, String description) { }
    public record UpdateRole(
            String name, String description, IamRecordStatus status, Long version, String reason) { }
    /** 角色 Permission 全量替换命令；version 必须来自最近一次角色 Permission 读取。 */
    public record PermissionAssignment(Set<String> permissionIds, Long version, String reason) { }
    public record Reason(String reason) { }
    public record ClientStatusChange(IamRecordStatus status, Long version, String reason) { }
}
