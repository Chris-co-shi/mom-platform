package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.model.IamDomainRules;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PartyType;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamUserAccessAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamUserAdminRepository;
import io.github.chrisshi.mom.security.token.MomJwtAuthorization;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * IAM 用户管理应用服务。
 *
 * <p>该服务承接用户查询、创建、资料更新、状态、解锁、凭证重置和逻辑删除用例。
 * 公开写方法保留原本地事务，用户行锁、最后管理员保护、Session 撤销与成功审计
 * 仍在同一事务中按原顺序执行。它不依赖 Controller 或 Servlet，底层失败向上传播。</p>
 */
public class IamUserAdminApplicationService {
    private final IamUserAdminRepository users;
    private final IamUserAccessAdminRepository access;
    private final PasswordEncoder passwordEncoder;
    private final IamAdminOperationSupport support;

    IamUserAdminApplicationService(
            IamUserAdminRepository users,
            IamUserAccessAdminRepository access,
            PasswordEncoder passwordEncoder,
            IamAdminOperationSupport support) {
        this.users = users;
        this.access = access;
        this.passwordEncoder = passwordEncoder;
        this.support = support;
    }

    /** 按已发布过滤与分页语义读取用户，无写副作用。 */
    public List<IamAdminViews.UserView> listUsers(
            Authentication authentication, String userType, String status, int limit, int offset) {
        support.requirePermission(authentication, "iam:user:read");
        return users.listUsers(userType, status,
                IamAdminCommandValidator.pageSize(limit), IamAdminCommandValidator.pageOffset(offset));
    }

    /** 读取单个用户；不存在时抛出稳定 NotFound 异常。 */
    public IamAdminViews.UserView getUser(Authentication authentication, String userId) {
        support.requirePermission(authentication, "iam:user:read");
        return support.requireUser(userId);
    }

    /** 创建用户并在同一事务追加安全审计。 */
    @Transactional
    public IamAdminViews.UserView createUser(
            Authentication authentication,
            IamAdminService.CreateUser command,
            IamAdminService.RequestContext request) {
        MomJwtAuthorization actor = support.actor(authentication, "iam:user:create");
        String username = IamAdminCommandValidator.requireUsername(command.username());
        String displayName = IamAdminCommandValidator.requireText(command.displayName(), "displayName", 200);
        UserType userType = Objects.requireNonNull(command.userType(), "userType 不能为空");
        String password = IamAdminCommandValidator.requireInitialPassword(command.initialPassword());
        Instant now = support.now();
        String userId = support.nextId();
        users.insertUser(
                userId, username, passwordEncoder.encode(password), displayName,
                userType, actor.userId(), now);
        if (userType != UserType.INTERNAL) {
            PartyType partyType = Objects.requireNonNull(command.partyType(), "外部用户必须提供 partyType");
            String partyId = IamAdminCommandValidator.requireId(command.partyId(), "partyId");
            IamDomainRules.requireExternalBinding(userType, partyType);
            access.rebindParty(userId, partyType, partyId, actor.userId(), now, support::nextId);
        }
        else if (command.partyType() != null || command.partyId() != null) {
            throw new IllegalArgumentException("INTERNAL 用户不得绑定外部 Party");
        }
        support.audit(actor, request, "iam.user.created", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                "user_created", null, IamAdminCommandValidator.json("userType", userType.name()));
        return support.requireUser(userId);
    }

    /** 持有用户行锁并按客户端版本更新展示名。 */
    @Transactional
    public IamAdminViews.UserView updateUser(
            Authentication authentication, String userId,
            IamAdminService.UpdateUser command, IamAdminService.RequestContext request) {
        MomJwtAuthorization actor = support.actor(authentication, "iam:user:update");
        IamAdminViews.UserView user = support.lockUser(userId);
        users.updateDisplayName(userId,
                IamAdminCommandValidator.requireText(command.displayName(), "displayName", 200),
                IamAdminCommandValidator.requireVersion(command.version(), user.version()),
                actor.userId(), support.now());
        support.audit(actor, request, "iam.user.updated", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.MEDIUM, "USER", userId, null,
                "user_profile_updated", null, IamAdminCommandValidator.json("field", "displayName"));
        return support.requireUser(userId);
    }

    /** 变更用户状态；禁用时执行自操作、最后管理员和 Session 撤销保护。 */
    @Transactional
    public IamAdminViews.UserView setUserStatus(
            Authentication authentication, String userId,
            IamAdminService.StatusChange command, IamAdminService.RequestContext request) {
        IamRecordStatus status = Objects.requireNonNull(command.status(), "status 不能为空");
        MomJwtAuthorization actor = support.actor(authentication,
                status == IamRecordStatus.ENABLED ? "iam:user:enable" : "iam:user:disable");
        IamAdminViews.UserView user = support.lockUser(userId);
        if (status == IamRecordStatus.DISABLED) {
            support.requireNotSelf(actor, userId, "不能禁用当前登录账号");
            support.protectPlatformAdminReduction(userId);
        }
        users.updateUserStatus(userId, status,
                IamAdminCommandValidator.requireVersion(command.version(), user.version()),
                actor.userId(), support.now());
        if (status == IamRecordStatus.DISABLED) {
            support.revokeUserSessions(userId, actor.userId(), "user_disabled");
        }
        support.audit(actor, request, "iam.user.status-changed", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"), null,
                IamAdminCommandValidator.json("status", status.name()));
        return support.requireUser(userId);
    }

    /** 清除临时锁定，版本冲突时整体回滚。 */
    @Transactional
    public IamAdminViews.UserView unlockUser(
            Authentication authentication, String userId,
            IamAdminService.VersionedReason command, IamAdminService.RequestContext request) {
        MomJwtAuthorization actor = support.actor(authentication, "iam:user:unlock");
        IamAdminViews.UserView user = support.lockUser(userId);
        users.unlockUser(userId,
                IamAdminCommandValidator.requireVersion(command.version(), user.version()),
                actor.userId(), support.now());
        support.audit(actor, request, "iam.user.unlocked", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"), null,
                IamAdminCommandValidator.json("locked", "false"));
        return support.requireUser(userId);
    }

    /** 管理员重置他人凭证并同步撤销该用户全部 Session。 */
    @Transactional
    public IamAdminViews.UserView resetPassword(
            Authentication authentication, String userId,
            IamAdminService.PasswordReset command, IamAdminService.RequestContext request) {
        MomJwtAuthorization actor = support.actor(authentication, "iam:user:password-reset");
        support.requireNotSelf(actor, userId, "管理员重置接口不能重置当前登录账号");
        IamAdminViews.UserView user = support.lockUser(userId);
        users.resetPassword(userId,
                passwordEncoder.encode(IamAdminCommandValidator.requireInitialPassword(command.temporaryPassword())),
                IamAdminCommandValidator.requireVersion(command.version(), user.version()),
                actor.userId(), support.now());
        support.revokeUserSessions(userId, actor.userId(), "credential_reset");
        support.audit(actor, request, "iam.user.credential-reset", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"), null,
                IamAdminCommandValidator.json("firstLoginChange", "required"));
        return support.requireUser(userId);
    }

    /** 逻辑删除他人账号，撤销 Session 后按版本更新并记录审计。 */
    @Transactional
    public void deleteUser(
            Authentication authentication, String userId,
            IamAdminService.VersionedReason command, IamAdminService.RequestContext request) {
        MomJwtAuthorization actor = support.actor(authentication, "iam:user:delete");
        support.requireNotSelf(actor, userId, "不能删除当前登录账号");
        IamAdminViews.UserView user = support.lockUser(userId);
        support.protectPlatformAdminReduction(userId);
        support.revokeUserSessions(userId, actor.userId(), "user_deleted");
        users.logicalDeleteUser(userId,
                IamAdminCommandValidator.requireVersion(command.version(), user.version()),
                actor.userId(), support.now());
        support.audit(actor, request, "iam.user.deleted", SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                IamAdminCommandValidator.requireReason(command.reason(), "reason"), null,
                IamAdminCommandValidator.json("deleted", "true"));
    }
}
