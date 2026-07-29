package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditActorType;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditResult;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.entity.IamSecurityAuditEventEntity;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamBuiltInAdministratorRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSecurityAuditEventAppender;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamSessionAdminRepository;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.admin.IamUserAdminRepository;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import io.github.chrisshi.mom.security.authorization.MomAuthorizationService;
import io.github.chrisshi.mom.security.token.MomJwtAuthorization;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.Instant;

/**
 * IAM Admin 用例共享的安全守卫、Session 撤销与追加型审计支撑。
 *
 * <p>该支撑类不定义交易边界；调用它的公开 Application Service 方法持有原本地
 * 事务。最后管理员保护仍先锁定唯一内置角色，Session 撤销仍同步调用唯一
 * {@link IamSessionTokenService}；任一底层失败向上传播并 Fail Closed。</p>
 */
final class IamAdminOperationSupport {
    private final IamUserAdminRepository users;
    private final IamSessionAdminRepository sessionQueries;
    private final IamBuiltInAdministratorRepository builtInAdministrators;
    private final MomAuthorizationService authorization;
    private final IamSessionTokenService sessions;
    private final IamSecurityAuditEventAppender auditEvents;
    private final IamSecureIdGenerator ids;
    private final Clock clock;

    IamAdminOperationSupport(
            IamUserAdminRepository users,
            IamSessionAdminRepository sessionQueries,
            IamBuiltInAdministratorRepository builtInAdministrators,
            MomAuthorizationService authorization,
            IamSessionTokenService sessions,
            IamSecurityAuditEventAppender auditEvents,
            IamSecureIdGenerator ids,
            Clock clock) {
        this.users = users;
        this.sessionQueries = sessionQueries;
        this.builtInAdministrators = builtInAdministrators;
        this.authorization = authorization;
        this.sessions = sessions;
        this.auditEvents = auditEvents;
        this.ids = ids;
        this.clock = clock;
    }

    MomJwtAuthorization actor(Authentication authentication, String permission) {
        authorization.requirePermission(authentication, permission);
        return authorization.current(authentication);
    }

    void requirePermission(Authentication authentication, String permission) {
        authorization.requirePermission(authentication, permission);
    }

    IamAdminViews.UserView requireUser(String userId) {
        return users.findUser(IamAdminCommandValidator.requireId(userId, "userId"))
                .orElseThrow(() -> new IamAdminExceptions.NotFound("用户不存在"));
    }

    IamAdminViews.UserView lockUser(String userId) {
        return users.lockUser(IamAdminCommandValidator.requireId(userId, "userId"))
                .orElseThrow(() -> new IamAdminExceptions.NotFound("用户不存在"));
    }

    void requireNotSelf(MomJwtAuthorization actor, String targetUserId, String message) {
        if (actor.userId().equals(targetUserId)) throw new IamAdminExceptions.Conflict(message);
    }

    void protectPlatformAdminReduction(String userId) {
        IamAdminViews.RoleView role = builtInAdministrators.lockPlatformAdminRole()
                .orElseThrow(() -> new IamAdminExceptions.Conflict("内置 PLATFORM_ADMIN 角色不存在"));
        if (role.status() != IamRecordStatus.ENABLED || role.applicableUserType() != UserType.INTERNAL) {
            throw new IamAdminExceptions.Conflict(
                    "内置 PLATFORM_ADMIN 角色必须保持 ENABLED 且适用于 INTERNAL");
        }
        Instant now = clock.instant();
        if (builtInAdministrators.isEffectivePlatformAdmin(userId, now)
                && builtInAdministrators.countEffectivePlatformAdministrators(now) <= 1) {
            throw new IamAdminExceptions.Conflict("系统必须至少保留一个有效 PLATFORM_ADMIN");
        }
    }

    int revokeUserSessions(String userId, String actor, String reason) {
        int count = 0;
        for (String sessionId : sessionQueries.activeSessionIdsForUser(userId)) {
            sessions.revoke(sessionId, actor, reason);
            count++;
        }
        return count;
    }

    void revokeSession(String sessionId, String actor, String reason) {
        sessions.revoke(sessionId, actor, reason);
    }

    Instant now() {
        return clock.instant();
    }

    String nextId() {
        return ids.nextId();
    }

    void audit(
            MomJwtAuthorization actor,
            IamAdminService.RequestContext request,
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
        event.setIpAddress(IamAdminCommandValidator.trim(request == null ? null : request.ipAddress(), 64));
        event.setUserAgent(IamAdminCommandValidator.trim(request == null ? null : request.userAgent(), 1000));
        event.setReasonCode(IamAdminCommandValidator.trim(reasonCode, 100));
        event.setReasonDetail(IamAdminCommandValidator.trim(reasonDetail, 2000));
        event.setChangeSummary(changeSummary == null ? "{}" : changeSummary);
        event.setCorrelationId(IamAdminCommandValidator.trim(CorrelationContext.currentId(), 128));
        event.setOccurredAt(now);
        event.setCreatedAt(now);
        auditEvents.append(event);
    }
}
