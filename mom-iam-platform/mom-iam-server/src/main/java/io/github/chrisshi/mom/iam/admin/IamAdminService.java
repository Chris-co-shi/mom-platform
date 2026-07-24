package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.model.IamDomainRules;
import io.github.chrisshi.mom.iam.domain.type.ApplicationCode;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditActorType;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditResult;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamSecurityAuditEventEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSecurityAuditEventAppender;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamAuthorizationAssignmentRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamClientPolicyAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamRoleAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamSecurityAuditQueryRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamSessionAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamUserAccessAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamUserAdminRepository;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import io.github.chrisshi.mom.security.authorization.MomAuthorizationService;
import io.github.chrisshi.mom.security.token.MomJwtAuthorization;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * S07 IAM 管理事务服务。
 *
 * <p>该应用服务位于 REST 适配器与 MyBatis Repository/安全端口之间，统一执行 Permission、安全约束、父聚合
 * 行锁、客户端版本校验、关系替换、版本推进、Session 撤销和追加型审计。所有关系写入均由
 * Spring 事务保证数据库内原子性；外部 Factory 校验不可用时 Fail Closed，绝不降级为放行。</p>
 */
public class IamAdminService {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z][A-Za-z0-9._@-]{2,119}");
    private static final Pattern MOM_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final int MAX_PAGE_SIZE = 200;

    private final IamUserAdminRepository users;
    private final IamRoleAdminRepository roles;
    private final IamAuthorizationAssignmentRepository assignments;
    private final IamUserAccessAdminRepository access;
    private final IamSessionAdminRepository sessionQueries;
    private final IamClientPolicyAdminRepository clients;
    private final IamSecurityAuditQueryRepository auditQueries;
    private final IamAdminReadModelRepository readModels;
    private final MomAuthorizationService authorization;
    private final PasswordEncoder passwordEncoder;
    private final IamSessionTokenService sessions;
    private final IamSecurityAuditEventAppender auditEvents;
    private final IamExternalFactoryScopeVerifier externalFactoryVerifier;
    private final IamSecureIdGenerator ids;
    private final Clock clock;

    public IamAdminService(
            IamUserAdminRepository users,
            IamRoleAdminRepository roles,
            IamAuthorizationAssignmentRepository assignments,
            IamUserAccessAdminRepository access,
            IamSessionAdminRepository sessionQueries,
            IamClientPolicyAdminRepository clients,
            IamSecurityAuditQueryRepository auditQueries,
            IamAdminReadModelRepository readModels,
            MomAuthorizationService authorization,
            PasswordEncoder passwordEncoder,
            IamSessionTokenService sessions,
            IamSecurityAuditEventAppender auditEvents,
            IamExternalFactoryScopeVerifier externalFactoryVerifier,
            IamSecureIdGenerator ids,
            Clock clock) {
        this.users = users;
        this.roles = roles;
        this.assignments = assignments;
        this.access = access;
        this.sessionQueries = sessionQueries;
        this.clients = clients;
        this.auditQueries = auditQueries;
        this.readModels = readModels;
        this.authorization = authorization;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.auditEvents = auditEvents;
        this.externalFactoryVerifier = externalFactoryVerifier;
        this.ids = ids;
        this.clock = clock;
    }

    public List<IamAdminViews.UserView> listUsers(
            Authentication authentication, String userType, String status, int limit, int offset) {
        authorization.requirePermission(authentication, "iam:user:read");
        return users.listUsers(userType, status, pageSize(limit), pageOffset(offset));
    }

    public IamAdminViews.UserView getUser(Authentication authentication, String userId) {
        authorization.requirePermission(authentication, "iam:user:read");
        return requireUser(userId);
    }

    /**
     * 读取用户授权聚合及其客户端并发版本，不产生数据库写入或审计副作用。
     *
     * @param authentication 当前管理员认证
     * @param userId 用户聚合 ID
     * @return 不含凭证材料的完整用户授权快照
     */
    public IamAdminViews.UserAuthorizationView getUserAuthorization(
            Authentication authentication, String userId) {
        authorization.requirePermission(authentication, "iam:user:read");
        return readModels.userAuthorization(requireId(userId, "userId"));
    }

    @Transactional
    public IamAdminViews.UserView createUser(
            Authentication authentication, CreateUser command, RequestContext request) {
        authorization.requirePermission(authentication, "iam:user:create");
        MomJwtAuthorization actor = authorization.current(authentication);
        String username = requireUsername(command.username());
        String displayName = requireText(command.displayName(), "displayName", 200);
        UserType userType = Objects.requireNonNull(command.userType(), "userType 不能为空");
        String password = requireInitialPassword(command.initialPassword());
        Instant now = clock.instant();
        String userId = ids.nextId();
        users.insertUser(
                userId, username, passwordEncoder.encode(password), displayName, userType, actor.userId(), now);

        if (userType != UserType.INTERNAL) {
            PartyType partyType = Objects.requireNonNull(command.partyType(), "外部用户必须提供 partyType");
            String partyId = requireId(command.partyId(), "partyId");
            IamDomainRules.requireExternalBinding(userType, partyType);
            access.rebindParty(userId, partyType, partyId, actor.userId(), now, ids::nextId);
        }
        else if (command.partyType() != null || command.partyId() != null) {
            throw new IllegalArgumentException("INTERNAL 用户不得绑定外部 Party");
        }

        audit(actor, request, "iam.user.created", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                "user_created", null, json("userType", userType.name()));
        return requireUser(userId);
    }

    @Transactional
    public IamAdminViews.UserView updateUser(
            Authentication authentication, String userId, UpdateUser command, RequestContext request) {
        authorization.requirePermission(authentication, "iam:user:update");
        MomJwtAuthorization actor = authorization.current(authentication);
        IamAdminViews.UserView user = lockUser(userId);
        users.updateDisplayName(
                userId, requireText(command.displayName(), "displayName", 200),
                requireVersion(command.version(), user.version()), actor.userId(), clock.instant());
        audit(actor, request, "iam.user.updated", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.MEDIUM, "USER", userId, null,
                "user_profile_updated", null, json("field", "displayName"));
        return requireUser(userId);
    }

    @Transactional
    public IamAdminViews.UserView setUserStatus(
            Authentication authentication, String userId, StatusChange command, RequestContext request) {
        IamRecordStatus status = Objects.requireNonNull(command.status(), "status 不能为空");
        String permission = status == IamRecordStatus.ENABLED ? "iam:user:enable" : "iam:user:disable";
        authorization.requirePermission(authentication, permission);
        MomJwtAuthorization actor = authorization.current(authentication);
        IamAdminViews.UserView user = lockUser(userId);
        if (status == IamRecordStatus.DISABLED) {
            requireNotSelf(actor, userId, "不能禁用当前登录账号");
            protectLastPlatformAdmin(userId);
        }
        users.updateUserStatus(
                userId, status, requireVersion(command.version(), user.version()), actor.userId(), clock.instant());
        if (status == IamRecordStatus.DISABLED) {
            revokeUserSessions(userId, actor.userId(), "user_disabled");
        }
        audit(actor, request, "iam.user.status-changed", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                requireReason(command.reason(), "reason"), null, json("status", status.name()));
        return requireUser(userId);
    }

    @Transactional
    public IamAdminViews.UserView unlockUser(
            Authentication authentication, String userId, VersionedReason command, RequestContext request) {
        authorization.requirePermission(authentication, "iam:user:unlock");
        MomJwtAuthorization actor = authorization.current(authentication);
        IamAdminViews.UserView user = lockUser(userId);
        users.unlockUser(
                userId, requireVersion(command.version(), user.version()), actor.userId(), clock.instant());
        audit(actor, request, "iam.user.unlocked", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                requireReason(command.reason(), "reason"), null, json("locked", "false"));
        return requireUser(userId);
    }

    @Transactional
    public IamAdminViews.UserView resetPassword(
            Authentication authentication, String userId, PasswordReset command, RequestContext request) {
        authorization.requirePermission(authentication, "iam:user:password-reset");
        MomJwtAuthorization actor = authorization.current(authentication);
        requireNotSelf(actor, userId, "管理员重置接口不能重置当前登录账号");
        IamAdminViews.UserView user = lockUser(userId);
        users.resetPassword(
                userId, passwordEncoder.encode(requireInitialPassword(command.temporaryPassword())),
                requireVersion(command.version(), user.version()), actor.userId(), clock.instant());
        revokeUserSessions(userId, actor.userId(), "credential_reset");
        audit(actor, request, "iam.user.credential-reset", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                requireReason(command.reason(), "reason"), null, json("firstLoginChange", "required"));
        return requireUser(userId);
    }

    @Transactional
    public void deleteUser(
            Authentication authentication, String userId, VersionedReason command, RequestContext request) {
        authorization.requirePermission(authentication, "iam:user:delete");
        MomJwtAuthorization actor = authorization.current(authentication);
        requireNotSelf(actor, userId, "不能删除当前登录账号");
        IamAdminViews.UserView user = lockUser(userId);
        protectLastPlatformAdmin(userId);
        revokeUserSessions(userId, actor.userId(), "user_deleted");
        users.logicalDeleteUser(
                userId, requireVersion(command.version(), user.version()), actor.userId(), clock.instant());
        audit(actor, request, "iam.user.deleted", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                requireReason(command.reason(), "reason"), null, json("deleted", "true"));
    }

    /**
     * 以用户聚合版本替换角色关系；版本、关系、父版本和成功审计在同一事务中提交或回滚。
     *
     * @param authentication 当前管理员认证
     * @param userId 用户聚合 ID
     * @param command 带读取版本的全量角色集合和审计原因
     * @param request 非敏感请求审计上下文
     * @return 版本推进后的完整用户授权快照
     * @throws IamAdminExceptions.StaleVersion 客户端版本过期；事务无关系或成功审计副作用
     */
    @Transactional
    public IamAdminViews.UserAuthorizationView replaceUserRoles(
            Authentication authentication, String userId, RoleAssignment command, RequestContext request) {
        authorization.requirePermission(authentication, "iam:user:role-assign");
        MomJwtAuthorization actor = authorization.current(authentication);
        IamAdminViews.UserView user = lockUser(userId);
        long version = requireVersion(command.version(), user.version());
        Set<String> roleIds = normalizedIds(command.roleIds(), "roleIds");
        List<IamAdminViews.RoleView> selectedRoles = roles.findRoles(roleIds);
        if (selectedRoles.size() != roleIds.size()) {
            throw new IamAdminExceptions.NotFound("存在无效角色");
        }
        for (IamAdminViews.RoleView role : selectedRoles) {
            IamDomainRules.requireRoleAssignment(user.userType(), role.applicableUserType());
            if (role.status() != IamRecordStatus.ENABLED) {
                throw new IamAdminExceptions.Conflict("禁用角色不能分配");
            }
        }
        boolean retainsPlatformAdmin = selectedRoles.stream()
                .anyMatch(role -> "PLATFORM_ADMIN".equals(role.code()));
        if (assignments.userHasEffectivePlatformAdmin(userId, clock.instant())
                && !retainsPlatformAdmin && assignments.effectivePlatformAdminCount(clock.instant()) <= 1) {
            throw new IamAdminExceptions.Conflict("系统必须至少保留一个有效 PLATFORM_ADMIN");
        }
        Instant now = clock.instant();
        assignments.replaceUserRoles(userId, roleIds, actor.userId(), now, ids::nextId);
        assignments.advanceUserVersion(userId, version, actor.userId(), now);
        audit(actor, request, "iam.user.roles-replaced", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                requireReason(command.reason(), "reason"), null,
                json("roleCount", Integer.toString(roleIds.size())));
        return readModels.userAuthorization(userId);
    }

    /**
     * 以用户聚合版本替换 Factory Scope；外部关系校验失败时不更新关系、版本或成功审计。
     *
     * @param authentication 当前管理员认证
     * @param userId 用户聚合 ID
     * @param command 带读取版本的全量 Factory 集合和审计原因
     * @param request 非敏感请求审计上下文
     * @return 版本推进后的完整用户授权快照
     * @throws IamAdminExceptions.StaleVersion 客户端版本过期；事务无关系或成功审计副作用
     */
    @Transactional
    public IamAdminViews.UserAuthorizationView replaceFactoryScopes(
            Authentication authentication, String userId, FactoryScopeChange command, RequestContext request) {
        authorization.requirePermission(authentication, "iam:user:factory-scope-assign");
        MomJwtAuthorization actor = authorization.current(authentication);
        IamAdminViews.UserView user = lockUser(userId);
        long version = requireVersion(command.version(), user.version());
        Set<String> factoryIds = normalizedIds(command.factoryIds(), "factoryIds");
        if (user.userType() != UserType.INTERNAL && !factoryIds.isEmpty()) {
            IamAdminViews.PartyBindingView binding = access.partyBinding(userId)
                    .filter(item -> item.status() == IamRecordStatus.ENABLED)
                    .orElseThrow(() -> new IamAdminExceptions.Conflict("外部用户缺少有效 Party Binding"));
            boolean allowed;
            try {
                allowed = externalFactoryVerifier.isAllowed(
                        binding.partyType(), binding.partyId(), factoryIds);
            }
            catch (RuntimeException exception) {
                throw new IamAdminExceptions.DependencyUnavailable(
                        "外部 Party 与 Factory 关系校验不可用");
            }
            if (!allowed) {
                throw new IamAdminExceptions.Conflict("外部 Factory Scope 不是有效业务关系工厂的子集");
            }
        }
        Instant now = clock.instant();
        access.replaceFactoryScopes(userId, factoryIds, actor.userId(), now, ids::nextId);
        assignments.advanceUserVersion(userId, version, actor.userId(), now);
        audit(actor, request, "iam.user.factory-scopes-replaced", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                requireReason(command.reason(), "reason"), null,
                json("factoryCount", Integer.toString(factoryIds.size())));
        return readModels.userAuthorization(userId);
    }

    /**
     * 以用户聚合版本变更移动端访问；禁用时的 Session 撤销与聚合变更共享事务边界。
     *
     * @param authentication 当前管理员认证
     * @param userId 用户聚合 ID
     * @param command 带读取版本的移动端访问状态和审计原因
     * @param request 非敏感请求审计上下文
     * @return 版本推进后的完整用户授权快照
     * @throws IamAdminExceptions.StaleVersion 客户端版本过期；不变更关系、不撤销 Session、不写成功审计
     */
    @Transactional
    public IamAdminViews.UserAuthorizationView setMobileAccess(
            Authentication authentication, String userId, MobileAccessChange command, RequestContext request) {
        authorization.requirePermission(authentication, "iam:user:mobile-access-manage");
        MomJwtAuthorization actor = authorization.current(authentication);
        IamAdminViews.UserView user = lockUser(userId);
        long version = requireVersion(command.version(), user.version());
        IamDomainRules.requireApplicationAccess(user.userType(), ApplicationCode.MOM_MOBILE_PDA);
        Instant now = clock.instant();
        access.setMobileAccess(
                userId, command.enabled(), actor.userId(), now, ids::nextId);
        if (!command.enabled()) {
            revokeUserSessions(userId, actor.userId(), "mobile_access_disabled");
        }
        assignments.advanceUserVersion(userId, version, actor.userId(), now);
        audit(actor, request, "iam.user.mobile-access-changed", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                requireReason(command.reason(), "reason"), null,
                json("enabled", Boolean.toString(command.enabled())));
        return readModels.userAuthorization(userId);
    }

    /**
     * 以用户聚合版本重绑外部 Party，并在成功时返回包含新版本的完整用户授权快照。
     *
     * @param authentication 当前管理员认证
     * @param userId 用户聚合 ID
     * @param command 带读取版本的 Party Binding 和审计原因
     * @param request 非敏感请求审计上下文
     * @return 版本推进后的完整用户授权快照
     * @throws IamAdminExceptions.StaleVersion 客户端版本过期；不变更绑定、不撤销 Session、不写成功审计
     */
    @Transactional
    public IamAdminViews.UserAuthorizationView rebindParty(
            Authentication authentication, String userId, PartyRebind command, RequestContext request) {
        authorization.requirePermission(authentication, "iam:user:party-rebind");
        MomJwtAuthorization actor = authorization.current(authentication);
        IamAdminViews.UserView user = lockUser(userId);
        long version = requireVersion(command.version(), user.version());
        PartyType partyType = Objects.requireNonNull(command.partyType(), "partyType 不能为空");
        IamDomainRules.requireExternalBinding(user.userType(), partyType);
        String partyId = requireId(command.partyId(), "partyId");
        Instant now = clock.instant();
        access.rebindParty(userId, partyType, partyId, actor.userId(), now, ids::nextId);
        revokeUserSessions(userId, actor.userId(), "party_rebound");
        assignments.advanceUserVersion(userId, version, actor.userId(), now);
        audit(actor, request, "iam.user.party-rebound", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                requireReason(command.reason(), "reason"), null,
                json("partyType", partyType.name()));
        return readModels.userAuthorization(userId);
    }

    public List<IamAdminViews.RoleView> listRoles(
            Authentication authentication, String userType, int limit, int offset) {
        authorization.requirePermission(authentication, "iam:role:read");
        return roles.listRoles(userType, pageSize(limit), pageOffset(offset));
    }

    /**
     * 读取角色 Permission 聚合及其客户端并发版本，不产生数据库写入或审计副作用。
     *
     * @param authentication 当前管理员认证
     * @param roleId 角色聚合 ID
     * @return 不含凭证材料的完整角色 Permission 快照
     */
    public IamAdminViews.RolePermissionView getRolePermissions(
            Authentication authentication, String roleId) {
        authorization.requirePermission(authentication, "iam:role:read");
        return readModels.rolePermissions(requireId(roleId, "roleId"));
    }

    @Transactional
    public IamAdminViews.RoleView createRole(
            Authentication authentication, CreateRole command, RequestContext request) {
        authorization.requirePermission(authentication, "iam:role:create");
        MomJwtAuthorization actor = authorization.current(authentication);
        String roleId = ids.nextId();
        String code = IamDomainRules.requireBusinessCode(command.code(), "roleCode");
        String name = requireText(command.name(), "name", 200);
        String description = optionalText(command.description(), 1000);
        UserType type = Objects.requireNonNull(command.applicableUserType(), "applicableUserType 不能为空");
        roles.insertRole(roleId, code, name, type, description, actor.userId(), clock.instant());
        audit(actor, request, "iam.role.created", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.MEDIUM, "ROLE", roleId, null,
                "role_created", null, json("code", code));
        return roles.lockRole(roleId).orElseThrow();
    }

    @Transactional
    public IamAdminViews.RoleView updateRole(
            Authentication authentication, String roleId, UpdateRole command, RequestContext request) {
        authorization.requirePermission(authentication, "iam:role:update");
        MomJwtAuthorization actor = authorization.current(authentication);
        IamAdminViews.RoleView role = lockRole(roleId);
        if (role.builtIn()) {
            throw new IamAdminExceptions.Conflict("内置角色在 P1.5 管理 API 中只读");
        }
        roles.updateRole(
                roleId, requireText(command.name(), "name", 200),
                optionalText(command.description(), 1000),
                Objects.requireNonNull(command.status(), "status 不能为空"),
                requireVersion(command.version(), role.version()), actor.userId(), clock.instant());
        audit(actor, request, "iam.role.updated", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.MEDIUM, "ROLE", roleId, null,
                requireReason(command.reason(), "reason"), null,
                json("status", command.status().name()));
        return roles.lockRole(roleId).orElseThrow();
    }

    /**
     * 以角色聚合版本替换 Permission 关系；内置角色继续保持只读，失败时事务整体回滚。
     *
     * @param authentication 当前管理员认证
     * @param roleId 角色聚合 ID
     * @param command 带读取版本的全量 Permission 集合和审计原因
     * @param request 非敏感请求审计上下文
     * @return 版本推进后的完整角色 Permission 快照
     * @throws IamAdminExceptions.StaleVersion 客户端版本过期；事务无关系或成功审计副作用
     */
    @Transactional
    public IamAdminViews.RolePermissionView replaceRolePermissions(
            Authentication authentication, String roleId, PermissionAssignment command, RequestContext request) {
        authorization.requirePermission(authentication, "iam:role:permission-manage");
        MomJwtAuthorization actor = authorization.current(authentication);
        IamAdminViews.RoleView role = lockRole(roleId);
        long version = requireVersion(command.version(), role.version());
        if (role.builtIn()) {
            throw new IamAdminExceptions.Conflict("内置角色 Permission 关系由 Flyway 管理");
        }
        Set<String> permissionIds = normalizedIds(command.permissionIds(), "permissionIds");
        if (roles.findEnabledPermissionIds(permissionIds).size() != permissionIds.size()) {
            throw new IamAdminExceptions.NotFound("存在无效或禁用 Permission");
        }
        Instant now = clock.instant();
        assignments.replaceRolePermissions(
                roleId, permissionIds, actor.userId(), now, ids::nextId);
        assignments.advanceRoleVersion(roleId, version, actor.userId(), now);
        audit(actor, request, "iam.role.permissions-replaced", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.HIGH, "ROLE", roleId, null,
                requireReason(command.reason(), "reason"), null,
                json("permissionCount", Integer.toString(permissionIds.size())));
        return readModels.rolePermissions(roleId);
    }

    public List<IamAdminViews.PermissionView> listPermissions(
            Authentication authentication, String domainCode, int limit, int offset) {
        authorization.requirePermission(authentication, "iam:permission:read");
        return roles.listPermissions(domainCode, pageSize(limit), pageOffset(offset));
    }

    public List<IamAdminViews.SessionView> listSessions(
            Authentication authentication, String userId, String status, int limit, int offset) {
        authorization.requirePermission(authentication, "iam:session:read");
        return sessionQueries.listSessions(userId, status, pageSize(limit), pageOffset(offset));
    }

    @Transactional
    public void revokeSession(
            Authentication authentication, String sessionId, Reason command, RequestContext request) {
        authorization.requirePermission(authentication, "iam:session:revoke");
        MomJwtAuthorization actor = authorization.current(authentication);
        String reason = requireReason(command.reason(), "reason");
        sessions.revoke(requireId(sessionId, "sessionId"), actor.userId(), reason);
        audit(actor, request, "iam.session.revoked", SecurityEventCategory.SESSION,
                PermissionRiskLevel.HIGH, "SESSION", sessionId, sessionId,
                reason, null, json("revoked", "true"));
    }

    @Transactional
    public int revokeAllSessions(
            Authentication authentication, String userId, Reason command, RequestContext request) {
        authorization.requirePermission(authentication, "iam:session:revoke-all");
        MomJwtAuthorization actor = authorization.current(authentication);
        requireUser(userId);
        String reason = requireReason(command.reason(), "reason");
        int count = revokeUserSessions(userId, actor.userId(), reason);
        audit(actor, request, "iam.user.sessions-revoked", SecurityEventCategory.SESSION,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                reason, null, json("sessionCount", Integer.toString(count)));
        return count;
    }

    public List<IamAdminViews.SecurityAuditView> listAudit(
            Authentication authentication, String category, String targetId, int limit, int offset) {
        authorization.requirePermission(authentication, "iam:audit:read");
        return auditQueries.listAudit(category, targetId, pageSize(limit), pageOffset(offset));
    }

    public List<IamAdminViews.ClientView> listClients(Authentication authentication) {
        authorization.requirePermission(authentication, "iam:client:read");
        return clients.listClients();
    }

    @Transactional
    public IamAdminViews.ClientView setClientStatus(
            Authentication authentication, String clientId, ClientStatusChange command, RequestContext request) {
        IamRecordStatus status = Objects.requireNonNull(command.status(), "status 不能为空");
        authorization.requirePermission(authentication,
                status == IamRecordStatus.ENABLED ? "iam:client:enable" : "iam:client:disable");
        MomJwtAuthorization actor = authorization.current(authentication);
        IamAdminViews.ClientView client = clients.lockClient(clientId)
                .orElseThrow(() -> new IamAdminExceptions.NotFound("Client Policy 不存在"));
        clients.updateClientStatus(
                clientId, status, requireVersion(command.version(), client.version()),
                actor.userId(), clock.instant());
        int revoked = 0;
        if (status == IamRecordStatus.DISABLED) {
            for (String sessionId : sessionQueries.activeSessionIdsForClient(clientId)) {
                sessions.revoke(sessionId, actor.userId(), "client_disabled");
                revoked++;
            }
        }
        audit(actor, request, "iam.client.status-changed", SecurityEventCategory.CLIENT,
                PermissionRiskLevel.HIGH, "CLIENT", clientId, null,
                requireReason(command.reason(), "reason"), null,
                json("revokedSessions", Integer.toString(revoked)));
        return clients.lockClient(clientId).orElseThrow();
    }

    private int revokeUserSessions(String userId, String actor, String reason) {
        int count = 0;
        for (String sessionId : sessionQueries.activeSessionIdsForUser(userId)) {
            sessions.revoke(sessionId, actor, reason);
            count++;
        }
        return count;
    }

    private void protectLastPlatformAdmin(String userId) {
        Instant now = clock.instant();
        if (assignments.userHasEffectivePlatformAdmin(userId, now)
                && assignments.effectivePlatformAdminCount(now) <= 1) {
            throw new IamAdminExceptions.Conflict("系统必须至少保留一个有效 PLATFORM_ADMIN");
        }
    }

    private IamAdminViews.UserView requireUser(String userId) {
        return users.findUser(requireId(userId, "userId"))
                .orElseThrow(() -> new IamAdminExceptions.NotFound("用户不存在"));
    }

    private IamAdminViews.UserView lockUser(String userId) {
        return users.lockUser(requireId(userId, "userId"))
                .orElseThrow(() -> new IamAdminExceptions.NotFound("用户不存在"));
    }

    private IamAdminViews.RoleView lockRole(String roleId) {
        return roles.lockRole(requireId(roleId, "roleId"))
                .orElseThrow(() -> new IamAdminExceptions.NotFound("角色不存在"));
    }

    private void audit(
            MomJwtAuthorization actor,
            RequestContext request,
            String eventType,
            SecurityEventCategory category,
            PermissionRiskLevel risk,
            String targetType,
            String targetId,
            String sessionId,
            String reasonCode,
            String reasonDetail,
            String changeSummary) {
        IamSecurityAuditEventEntity event = new IamSecurityAuditEventEntity();
        Instant now = clock.instant();
        event.setId(ids.nextId());
        event.setEventType(eventType);
        event.setEventCategory(category);
        event.setRiskLevel(risk);
        event.setResult(SecurityAuditResult.SUCCESS);
        event.setActorType(SecurityAuditActorType.ADMIN);
        event.setActorUserId(actor.userId());
        event.setActorClientId(actor.clientId());
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setSessionId(sessionId);
        event.setIpAddress(trim(request == null ? null : request.ipAddress(), 64));
        event.setUserAgent(trim(request == null ? null : request.userAgent(), 1000));
        event.setReasonCode(trim(reasonCode, 100));
        event.setReasonDetail(trim(reasonDetail, 2000));
        event.setChangeSummary(changeSummary == null ? "{}" : changeSummary);
        event.setCorrelationId(trim(CorrelationContext.currentId(), 128));
        event.setOccurredAt(now);
        event.setCreatedAt(now);
        auditEvents.append(event);
    }

    private static void requireNotSelf(MomJwtAuthorization actor, String targetUserId, String message) {
        if (actor.userId().equals(targetUserId)) {
            throw new IamAdminExceptions.Conflict(message);
        }
    }

    private static long requireVersion(Long requested, long current) {
        if (requested == null) {
            throw new IllegalArgumentException("version 不能为空");
        }
        if (requested != current) {
            throw new IamAdminExceptions.StaleVersion("version 已过期，请重新读取后重试");
        }
        return current;
    }

    private static int pageSize(int value) {
        return value <= 0 ? 50 : Math.min(value, MAX_PAGE_SIZE);
    }

    private static int pageOffset(int value) {
        return Math.max(0, value);
    }

    private static String requireUsername(String value) {
        String normalized = requireText(value, "username", 120);
        if (!USERNAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("username 格式无效");
        }
        return normalized;
    }

    private static String requireInitialPassword(String value) {
        if (value == null || value.length() < 12 || value.length() > 200) {
            throw new IllegalArgumentException("初始凭证长度必须为 12～200 个字符");
        }
        return value;
    }

    private static String requireReason(String value, String name) {
        return requireText(value, name, 100);
    }

    private static String requireId(String value, String name) {
        String normalized = requireText(value, name, 128);
        if ((name.endsWith("Id") || name.endsWith("Ids")) && !MOM_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " 必须是 19 位以内正数字符串 ID");
        }
        return normalized;
    }

    private static Set<String> normalizedIds(Set<String> values, String name) {
        if (values == null) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            result.add(requireId(value, name));
        }
        return Set.copyOf(result);
    }

    private static String requireText(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new IllegalArgumentException(name + " 长度不能超过 " + max);
        }
        return normalized;
    }

    private static String optionalText(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("文本长度不能超过 " + max);
        return normalized;
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private static String json(String key, String value) {
        return "{\"" + escape(key) + "\":\"" + escape(value) + "\"}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
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
