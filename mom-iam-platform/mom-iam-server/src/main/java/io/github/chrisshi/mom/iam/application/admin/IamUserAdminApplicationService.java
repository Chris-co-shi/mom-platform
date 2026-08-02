package io.github.chrisshi.mom.iam.application.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminActor;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminCommands;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminRequestContext;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.application.admin.port.IamIdentifierGenerator;
import io.github.chrisshi.mom.iam.application.admin.port.IamPasswordHasher;
import io.github.chrisshi.mom.iam.application.admin.port.IamUserAccessPort;
import io.github.chrisshi.mom.iam.application.admin.port.IamUserAdminQueryPort;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.domain.user.IamUserAccount;
import io.github.chrisshi.mom.iam.domain.user.IamUserAccountRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** IAM 用户账号管理用例。 */
public class IamUserAdminApplicationService {
    private final IamUserAccountRepository users;
    private final IamUserAdminQueryPort userQueries;
    private final IamUserAccessPort access;
    private final IamPasswordHasher passwordHasher;
    private final IamIdentifierGenerator ids;
    private final IamAdminAuditService audits;
    private final IamSessionRevocationService revocations;
    private final IamPlatformAdministratorGuard platformAdministrators;
    private final Clock clock;

    public IamUserAdminApplicationService(
            IamUserAccountRepository users,
            IamUserAdminQueryPort userQueries,
            IamUserAccessPort access,
            IamPasswordHasher passwordHasher,
            IamIdentifierGenerator ids,
            IamAdminAuditService audits,
            IamSessionRevocationService revocations,
            IamPlatformAdministratorGuard platformAdministrators,
            Clock clock) {
        this.users = users;
        this.userQueries = userQueries;
        this.access = access;
        this.passwordHasher = passwordHasher;
        this.ids = ids;
        this.audits = audits;
        this.revocations = revocations;
        this.platformAdministrators = platformAdministrators;
        this.clock = clock;
    }

    public List<IamAdminViews.UserView> listUsers(
            IamAdminActor actor, String userType, String status, int limit, int offset) {
        actor.requirePermission("iam:user:read");
        return userQueries.listUsers(
                userType, status,
                IamAdminCommandValidator.pageSize(limit),
                IamAdminCommandValidator.pageOffset(offset));
    }

    public IamAdminViews.UserView getUser(IamAdminActor actor, String userId) {
        actor.requirePermission("iam:user:read");
        return requireUserView(userId);
    }

    @Transactional
    public IamAdminViews.UserView createUser(
            IamAdminActor actor,
            IamAdminCommands.CreateUser command,
            IamAdminRequestContext request) {
        actor.requirePermission("iam:user:create");
        String username = IamAdminCommandValidator.requireUsername(command.username());
        String displayName =
                IamAdminCommandValidator.requireText(command.displayName(), "displayName", 200);
        UserType userType = Objects.requireNonNull(command.userType(), "userType 不能为空");
        String rawPassword =
                IamAdminCommandValidator.requireInitialPassword(command.initialPassword());
        String userId = ids.nextId();
        IamUserAccount user = IamUserAccount.create(userId, username, displayName, userType);
        Instant now = clock.instant();
        users.create(user, passwordHasher.hash(rawPassword), actor.userId(), now);

        if (user.external()) {
            PartyType partyType =
                    Objects.requireNonNull(command.partyType(), "外部用户必须提供 partyType");
            String partyId = IamAdminCommandValidator.requireId(command.partyId(), "partyId");
            IamAdminFailures.fromDomain(() -> user.requirePartyBinding(partyType));
            access.rebindParty(
                    userId, partyType, partyId, actor.userId(), now, ids::nextId);
        }
        else if (command.partyType() != null || command.partyId() != null) {
            throw new IllegalArgumentException("INTERNAL 用户不得绑定外部 Party");
        }

        audits.record(
                actor, request, "iam.user.created", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                "user_created", null,
                IamAdminCommandValidator.json("userType", userType.name()));
        return requireUserView(userId);
    }

    @Transactional
    public IamAdminViews.UserView updateUser(
            IamAdminActor actor,
            String userId,
            IamAdminCommands.UpdateUser command,
            IamAdminRequestContext request) {
        actor.requirePermission("iam:user:update");
        IamUserAccount user = lockUser(userId);
        var decision = IamAdminFailures.fromDomain(() -> user.changeDisplayName(
                IamAdminCommandValidator.requireText(
                        command.displayName(), "displayName", 200),
                command.version()));
        IamAdminFailures.fromDomain(() -> users.updateDisplayName(
                user.id(), decision.displayName(), decision.expectedVersion(),
                actor.userId(), clock.instant()));
        audits.record(
                actor, request, "iam.user.updated", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.MEDIUM, "USER", user.id(), null,
                "user_profile_updated", null,
                IamAdminCommandValidator.json("field", "displayName"));
        return requireUserView(user.id());
    }

    @Transactional
    public IamAdminViews.UserView setUserStatus(
            IamAdminActor actor,
            String userId,
            IamAdminCommands.StatusChange command,
            IamAdminRequestContext request) {
        IamRecordStatus status = Objects.requireNonNull(command.status(), "status 不能为空");
        actor.requirePermission(
                status == IamRecordStatus.ENABLED ? "iam:user:enable" : "iam:user:disable");
        IamUserAccount user = lockUser(userId);
        var decision = IamAdminFailures.fromDomain(() ->
                user.changeStatus(actor.userId(), status, command.version()));
        if (decision.revokeSessions()) platformAdministrators.protectReduction(user.id());
        IamAdminFailures.fromDomain(() -> users.updateStatus(
                user.id(), decision.status(), decision.expectedVersion(),
                actor.userId(), clock.instant()));
        if (decision.revokeSessions()) {
            revocations.revokeUserSessions(
                    user.id(), actor.userId(), "user_disabled");
        }
        audits.record(
                actor, request, "iam.user.status-changed", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH, "USER", user.id(), null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"),
                null, IamAdminCommandValidator.json("status", status.name()));
        return requireUserView(user.id());
    }

    @Transactional
    public IamAdminViews.UserView unlockUser(
            IamAdminActor actor,
            String userId,
            IamAdminCommands.VersionedReason command,
            IamAdminRequestContext request) {
        actor.requirePermission("iam:user:unlock");
        IamUserAccount user = lockUser(userId);
        var decision = IamAdminFailures.fromDomain(() -> user.unlock(command.version()));
        IamAdminFailures.fromDomain(() -> users.unlock(
                user.id(), decision.expectedVersion(), actor.userId(), clock.instant()));
        audits.record(
                actor, request, "iam.user.unlocked", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH, "USER", user.id(), null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"),
                null, IamAdminCommandValidator.json("locked", "false"));
        return requireUserView(user.id());
    }

    @Transactional
    public IamAdminViews.UserView resetPassword(
            IamAdminActor actor,
            String userId,
            IamAdminCommands.PasswordReset command,
            IamAdminRequestContext request) {
        actor.requirePermission("iam:user:password-reset");
        IamUserAccount user = lockUser(userId);
        var decision = IamAdminFailures.fromDomain(() ->
                user.resetCredential(actor.userId(), command.version()));
        String password = IamAdminCommandValidator.requireInitialPassword(
                command.temporaryPassword());
        IamAdminFailures.fromDomain(() -> users.resetCredential(
                user.id(), passwordHasher.hash(password), decision.expectedVersion(),
                actor.userId(), clock.instant()));
        revocations.revokeUserSessions(
                user.id(), actor.userId(), "credential_reset");
        audits.record(
                actor, request, "iam.user.credential-reset", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH, "USER", user.id(), null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"),
                null,
                IamAdminCommandValidator.json("firstLoginChange", "required"));
        return requireUserView(user.id());
    }

    @Transactional
    public void deleteUser(
            IamAdminActor actor,
            String userId,
            IamAdminCommands.VersionedReason command,
            IamAdminRequestContext request) {
        actor.requirePermission("iam:user:delete");
        IamUserAccount user = lockUser(userId);
        var decision = IamAdminFailures.fromDomain(() ->
                user.delete(actor.userId(), command.version()));
        platformAdministrators.protectReduction(user.id());
        revocations.revokeUserSessions(
                user.id(), actor.userId(), "user_deleted");
        IamAdminFailures.fromDomain(() -> users.logicalDelete(
                user.id(), decision.expectedVersion(), actor.userId(), clock.instant()));
        audits.record(
                actor, request, "iam.user.deleted", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH, "USER", user.id(), null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"),
                null, IamAdminCommandValidator.json("deleted", "true"));
    }

    IamUserAccount lockUser(String userId) {
        return users.lockById(IamAdminCommandValidator.requireId(userId, "userId"))
                .orElseThrow(() -> new IamAdminExceptions.NotFound("用户不存在"));
    }

    private IamAdminViews.UserView requireUserView(String userId) {
        return userQueries.findUser(IamAdminCommandValidator.requireId(userId, "userId"))
                .orElseThrow(() -> new IamAdminExceptions.NotFound("用户不存在"));
    }
}
