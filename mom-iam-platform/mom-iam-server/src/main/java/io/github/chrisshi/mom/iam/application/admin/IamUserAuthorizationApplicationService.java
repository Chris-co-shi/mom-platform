package io.github.chrisshi.mom.iam.application.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminActor;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminCommands;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminRequestContext;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.application.admin.port.IamAuthorizationAssignmentPort;
import io.github.chrisshi.mom.iam.application.admin.port.IamAuthorizationReadPort;
import io.github.chrisshi.mom.iam.application.admin.port.IamExternalFactoryScopeVerifier;
import io.github.chrisshi.mom.iam.application.admin.port.IamIdentifierGenerator;
import io.github.chrisshi.mom.iam.application.admin.port.IamUserAccessPort;
import io.github.chrisshi.mom.iam.domain.authorization.IamUserAuthorization;
import io.github.chrisshi.mom.iam.domain.role.IamRole;
import io.github.chrisshi.mom.iam.domain.role.IamRoleRepository;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;
import io.github.chrisshi.mom.iam.domain.user.IamUserAccount;
import io.github.chrisshi.mom.iam.domain.user.IamUserAccountRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** IAM 用户授权聚合用例。 */
public class IamUserAuthorizationApplicationService {
    private final IamUserAccountRepository users;
    private final IamRoleRepository roles;
    private final IamAuthorizationAssignmentPort assignments;
    private final IamUserAccessPort access;
    private final IamAuthorizationReadPort readModels;
    private final IamExternalFactoryScopeVerifier externalFactoryVerifier;
    private final IamIdentifierGenerator ids;
    private final IamAdminAuditService audits;
    private final IamSessionRevocationService revocations;
    private final IamPlatformAdministratorGuard platformAdministrators;
    private final Clock clock;

    public IamUserAuthorizationApplicationService(
            IamUserAccountRepository users,
            IamRoleRepository roles,
            IamAuthorizationAssignmentPort assignments,
            IamUserAccessPort access,
            IamAuthorizationReadPort readModels,
            IamExternalFactoryScopeVerifier externalFactoryVerifier,
            IamIdentifierGenerator ids,
            IamAdminAuditService audits,
            IamSessionRevocationService revocations,
            IamPlatformAdministratorGuard platformAdministrators,
            Clock clock) {
        this.users = users;
        this.roles = roles;
        this.assignments = assignments;
        this.access = access;
        this.readModels = readModels;
        this.externalFactoryVerifier = externalFactoryVerifier;
        this.ids = ids;
        this.audits = audits;
        this.revocations = revocations;
        this.platformAdministrators = platformAdministrators;
        this.clock = clock;
    }

    public IamAdminViews.UserAuthorizationView getUserAuthorization(
            IamAdminActor actor, String userId) {
        actor.requirePermission("iam:user:read");
        return requireAuthorization(userId);
    }

    @Transactional
    public IamAdminViews.UserAuthorizationView replaceUserRoles(
            IamAdminActor actor,
            String userId,
            IamAdminCommands.RoleAssignment command,
            IamAdminRequestContext request) {
        actor.requirePermission("iam:user:role-assign");
        IamUserAccount user = lockUser(userId);
        IamAdminFailures.fromDomain(() -> user.requireVersion(command.version()));
        Set<String> roleIds =
                IamAdminCommandValidator.normalizedIds(command.roleIds(), "roleIds");
        List<IamRole> selectedRoles = roles.findByIds(roleIds);
        if (selectedRoles.size() != roleIds.size()) {
            throw new IamAdminExceptions.NotFound("存在无效角色");
        }
        var decision = IamAdminFailures.fromDomain(() ->
                authorization(user).replaceRoles(
                        roleIds, selectedRoles, command.version()));
        if (!decision.retainsPlatformAdministrator()) {
            platformAdministrators.protectReduction(user.id());
        }
        Instant now = clock.instant();
        assignments.replaceUserRoles(
                user.id(), decision.roleIds(), actor.userId(), now, ids::nextId);
        IamAdminFailures.fromDomain(() -> assignments.advanceUserVersion(
                user.id(), decision.expectedVersion(), actor.userId(), now));
        audits.record(
                actor, request, "iam.user.roles-replaced",
                SecurityEventCategory.AUTHORIZATION, PermissionRiskLevel.HIGH,
                "USER", user.id(), null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"),
                null,
                IamAdminCommandValidator.json(
                        "roleCount", Integer.toString(roleIds.size())));
        return requireAuthorization(user.id());
    }

    @Transactional
    public IamAdminViews.UserAuthorizationView replaceFactoryScopes(
            IamAdminActor actor,
            String userId,
            IamAdminCommands.FactoryScopeChange command,
            IamAdminRequestContext request) {
        actor.requirePermission("iam:user:factory-scope-assign");
        IamUserAccount user = lockUser(userId);
        Set<String> factoryIds =
                IamAdminCommandValidator.normalizedIds(command.factoryIds(), "factoryIds");
        IamUserAuthorization authorization = authorization(user);
        var decision = IamAdminFailures.fromDomain(() ->
                authorization.replaceFactoryScopes(factoryIds, command.version()));
        if (user.external() && !factoryIds.isEmpty()) {
            var binding = Objects.requireNonNull(
                    authorization.partyBinding(), "外部用户缺少 Party Binding");
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
                throw new IamAdminExceptions.Conflict(
                        "外部 Factory Scope 不是有效业务关系工厂的子集");
            }
        }
        Instant now = clock.instant();
        access.replaceFactoryScopes(
                user.id(), decision.factoryIds(), actor.userId(), now, ids::nextId);
        IamAdminFailures.fromDomain(() -> assignments.advanceUserVersion(
                user.id(), decision.expectedVersion(), actor.userId(), now));
        audits.record(
                actor, request, "iam.user.factory-scopes-replaced",
                SecurityEventCategory.AUTHORIZATION, PermissionRiskLevel.HIGH,
                "USER", user.id(), null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"),
                null,
                IamAdminCommandValidator.json(
                        "factoryCount", Integer.toString(factoryIds.size())));
        return requireAuthorization(user.id());
    }

    @Transactional
    public IamAdminViews.UserAuthorizationView setMobileAccess(
            IamAdminActor actor,
            String userId,
            IamAdminCommands.MobileAccessChange command,
            IamAdminRequestContext request) {
        actor.requirePermission("iam:user:mobile-access-manage");
        IamUserAccount user = lockUser(userId);
        var decision = IamAdminFailures.fromDomain(() ->
                authorization(user).setMobileAccess(
                        command.enabled(), command.version()));
        Instant now = clock.instant();
        access.setMobileAccess(
                user.id(), decision.enabled(), actor.userId(), now, ids::nextId);
        if (decision.revokeSessions()) {
            revocations.revokeUserSessions(
                    user.id(), actor.userId(), "mobile_access_disabled");
        }
        IamAdminFailures.fromDomain(() -> assignments.advanceUserVersion(
                user.id(), decision.expectedVersion(), actor.userId(), now));
        audits.record(
                actor, request, "iam.user.mobile-access-changed",
                SecurityEventCategory.AUTHORIZATION, PermissionRiskLevel.HIGH,
                "USER", user.id(), null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"),
                null,
                IamAdminCommandValidator.json(
                        "enabled", Boolean.toString(command.enabled())));
        return requireAuthorization(user.id());
    }

    @Transactional
    public IamAdminViews.UserAuthorizationView rebindParty(
            IamAdminActor actor,
            String userId,
            IamAdminCommands.PartyRebind command,
            IamAdminRequestContext request) {
        actor.requirePermission("iam:user:party-rebind");
        IamUserAccount user = lockUser(userId);
        PartyType partyType = Objects.requireNonNull(
                command.partyType(), "partyType 不能为空");
        String partyId = IamAdminCommandValidator.requireId(
                command.partyId(), "partyId");
        var decision = IamAdminFailures.fromDomain(() ->
                authorization(user).rebindParty(
                        partyType, partyId, command.version()));
        Instant now = clock.instant();
        access.rebindParty(
                user.id(), decision.partyType(), decision.partyId(),
                actor.userId(), now, ids::nextId);
        if (decision.revokeSessions()) {
            revocations.revokeUserSessions(
                    user.id(), actor.userId(), "party_rebound");
        }
        IamAdminFailures.fromDomain(() -> assignments.advanceUserVersion(
                user.id(), decision.expectedVersion(), actor.userId(), now));
        audits.record(
                actor, request, "iam.user.party-rebound",
                SecurityEventCategory.AUTHORIZATION, PermissionRiskLevel.HIGH,
                "USER", user.id(), null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"),
                null,
                IamAdminCommandValidator.json("partyType", partyType.name()));
        return requireAuthorization(user.id());
    }

    private IamUserAuthorization authorization(IamUserAccount user) {
        return new IamUserAuthorization(
                user, access.partyBinding(user.id()).orElse(null));
    }

    private IamUserAccount lockUser(String userId) {
        return users.lockById(IamAdminCommandValidator.requireId(userId, "userId"))
                .orElseThrow(() -> new IamAdminExceptions.NotFound("用户不存在"));
    }

    private IamAdminViews.UserAuthorizationView requireAuthorization(String userId) {
        return readModels.userAuthorization(
                        IamAdminCommandValidator.requireId(userId, "userId"))
                .orElseThrow(() -> new IamAdminExceptions.NotFound("用户不存在"));
    }
}
