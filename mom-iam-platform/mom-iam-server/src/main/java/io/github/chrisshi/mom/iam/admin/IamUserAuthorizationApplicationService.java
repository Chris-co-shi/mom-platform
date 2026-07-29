package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.model.IamDomainRules;
import io.github.chrisshi.mom.iam.domain.type.ApplicationCode;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamAuthorizationAssignmentRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamRoleAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamUserAccessAdminRepository;
import io.github.chrisshi.mom.security.token.MomJwtAuthorization;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * IAM 用户授权关系应用服务。
 *
 * <p>该服务专注用户角色、Factory Scope、Mobile Access 与 Party Binding 聚合。
 * 每个写用例仍先锁用户父聚合，校验客户端版本，再替换关系、必要时同步撤销
 * Session、推进父版本并写成功审计。外部 Factory 校验失败时 Fail Closed；服务
 * 不依赖 HTTP DTO、Mapper 或 Token 实现。</p>
 */
public class IamUserAuthorizationApplicationService {
    private final IamRoleAdminRepository roles;
    private final IamAuthorizationAssignmentRepository assignments;
    private final IamUserAccessAdminRepository access;
    private final IamAdminReadModelRepository readModels;
    private final IamExternalFactoryScopeVerifier externalFactoryVerifier;
    private final IamAdminOperationSupport support;

    IamUserAuthorizationApplicationService(
            IamRoleAdminRepository roles,
            IamAuthorizationAssignmentRepository assignments,
            IamUserAccessAdminRepository access,
            IamAdminReadModelRepository readModels,
            IamExternalFactoryScopeVerifier externalFactoryVerifier,
            IamAdminOperationSupport support) {
        this.roles = roles;
        this.assignments = assignments;
        this.access = access;
        this.readModels = readModels;
        this.externalFactoryVerifier = externalFactoryVerifier;
        this.support = support;
    }

    /** 读取含父聚合版本的用户授权快照，无写副作用。 */
    public IamAdminViews.UserAuthorizationView getUserAuthorization(
            Authentication authentication, String userId) {
        support.requirePermission(authentication, "iam:user:read");
        return readModels.userAuthorization(IamAdminCommandValidator.requireId(userId, "userId"));
    }

    /** 按用户聚合版本全量替换角色关系。 */
    @Transactional
    public IamAdminViews.UserAuthorizationView replaceUserRoles(
            Authentication authentication, String userId,
            IamAdminService.RoleAssignment command, IamAdminService.RequestContext request) {
        MomJwtAuthorization actor = support.actor(authentication, "iam:user:role-assign");
        IamAdminViews.UserView user = support.lockUser(userId);
        long version = IamAdminCommandValidator.requireVersion(command.version(), user.version());
        Set<String> roleIds = IamAdminCommandValidator.normalizedIds(command.roleIds(), "roleIds");
        List<IamAdminViews.RoleView> selectedRoles = roles.findRoles(roleIds);
        if (selectedRoles.size() != roleIds.size()) throw new IamAdminExceptions.NotFound("存在无效角色");
        for (IamAdminViews.RoleView role : selectedRoles) {
            IamDomainRules.requireRoleAssignment(user.userType(), role.applicableUserType());
            if (role.status() != IamRecordStatus.ENABLED) {
                throw new IamAdminExceptions.Conflict("禁用角色不能分配");
            }
        }
        boolean retainsPlatformAdmin = selectedRoles.stream()
                .anyMatch(role -> "PLATFORM_ADMIN".equals(role.code()));
        if (!retainsPlatformAdmin) support.protectPlatformAdminReduction(userId);
        Instant now = support.now();
        assignments.replaceUserRoles(userId, roleIds, actor.userId(), now, support::nextId);
        assignments.advanceUserVersion(userId, version, actor.userId(), now);
        support.audit(actor, request, "iam.user.roles-replaced", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"), null,
                IamAdminCommandValidator.json("roleCount", Integer.toString(roleIds.size())));
        return readModels.userAuthorization(userId);
    }

    /** 权威校验外部关系后按用户版本全量替换 Factory Scope。 */
    @Transactional
    public IamAdminViews.UserAuthorizationView replaceFactoryScopes(
            Authentication authentication, String userId,
            IamAdminService.FactoryScopeChange command, IamAdminService.RequestContext request) {
        MomJwtAuthorization actor = support.actor(authentication, "iam:user:factory-scope-assign");
        IamAdminViews.UserView user = support.lockUser(userId);
        long version = IamAdminCommandValidator.requireVersion(command.version(), user.version());
        Set<String> factoryIds = IamAdminCommandValidator.normalizedIds(command.factoryIds(), "factoryIds");
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
                throw new IamAdminExceptions.DependencyUnavailable("外部 Party 与 Factory 关系校验不可用");
            }
            if (!allowed) {
                throw new IamAdminExceptions.Conflict("外部 Factory Scope 不是有效业务关系工厂的子集");
            }
        }
        Instant now = support.now();
        access.replaceFactoryScopes(userId, factoryIds, actor.userId(), now, support::nextId);
        assignments.advanceUserVersion(userId, version, actor.userId(), now);
        support.audit(actor, request, "iam.user.factory-scopes-replaced", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"), null,
                IamAdminCommandValidator.json("factoryCount", Integer.toString(factoryIds.size())));
        return readModels.userAuthorization(userId);
    }

    /** 变更 Mobile Access；禁用时同步撤销用户 Session。 */
    @Transactional
    public IamAdminViews.UserAuthorizationView setMobileAccess(
            Authentication authentication, String userId,
            IamAdminService.MobileAccessChange command, IamAdminService.RequestContext request) {
        MomJwtAuthorization actor = support.actor(authentication, "iam:user:mobile-access-manage");
        IamAdminViews.UserView user = support.lockUser(userId);
        long version = IamAdminCommandValidator.requireVersion(command.version(), user.version());
        IamDomainRules.requireApplicationAccess(user.userType(), ApplicationCode.MOM_MOBILE_PDA);
        Instant now = support.now();
        access.setMobileAccess(userId, command.enabled(), actor.userId(), now, support::nextId);
        if (!command.enabled()) support.revokeUserSessions(userId, actor.userId(), "mobile_access_disabled");
        assignments.advanceUserVersion(userId, version, actor.userId(), now);
        support.audit(actor, request, "iam.user.mobile-access-changed", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"), null,
                IamAdminCommandValidator.json("enabled", Boolean.toString(command.enabled())));
        return readModels.userAuthorization(userId);
    }

    /** 重绑外部 Party，撤销用户 Session 并推进父聚合版本。 */
    @Transactional
    public IamAdminViews.UserAuthorizationView rebindParty(
            Authentication authentication, String userId,
            IamAdminService.PartyRebind command, IamAdminService.RequestContext request) {
        MomJwtAuthorization actor = support.actor(authentication, "iam:user:party-rebind");
        IamAdminViews.UserView user = support.lockUser(userId);
        long version = IamAdminCommandValidator.requireVersion(command.version(), user.version());
        PartyType partyType = Objects.requireNonNull(command.partyType(), "partyType 不能为空");
        IamDomainRules.requireExternalBinding(user.userType(), partyType);
        String partyId = IamAdminCommandValidator.requireId(command.partyId(), "partyId");
        Instant now = support.now();
        access.rebindParty(userId, partyType, partyId, actor.userId(), now, support::nextId);
        support.revokeUserSessions(userId, actor.userId(), "party_rebound");
        assignments.advanceUserVersion(userId, version, actor.userId(), now);
        support.audit(actor, request, "iam.user.party-rebound", SecurityEventCategory.AUTHORIZATION,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"), null,
                IamAdminCommandValidator.json("partyType", partyType.name()));
        return readModels.userAuthorization(userId);
    }
}
