package io.github.chrisshi.mom.iam.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamSessionAdminRepository;
import io.github.chrisshi.mom.security.token.MomJwtAuthorization;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * IAM Session 管理应用服务。
 *
 * <p>该服务仅组织管理端 Session 查询、单 Session 撤销和用户全部 Session 撤销。
 * 写方法保留原本地事务并同步调用唯一 Session Token Service，撤销成功后才追加
 * 成功审计；Redis 或持久化失败不被吞掉，保持 Fail Closed。</p>
 */
public class IamSessionAdminApplicationService {
    private final IamSessionAdminRepository sessionQueries;
    private final IamAdminOperationSupport support;

    IamSessionAdminApplicationService(
            IamSessionAdminRepository sessionQueries,
            IamAdminOperationSupport support) {
        this.sessionQueries = sessionQueries;
        this.support = support;
    }

    /** 按已发布过滤与分页语义读取非敏感 Session 投影。 */
    public List<IamAdminViews.SessionView> listSessions(
            Authentication authentication, String userId, String status, int limit, int offset) {
        support.requirePermission(authentication, "iam:session:read");
        return sessionQueries.listSessions(userId, status,
                IamAdminCommandValidator.pageSize(limit), IamAdminCommandValidator.pageOffset(offset));
    }

    /** 撤销单 Session，撤销失败时不写成功审计。 */
    @Transactional
    public void revokeSession(
            Authentication authentication, String sessionId,
            IamAdminService.Reason command, IamAdminService.RequestContext request) {
        MomJwtAuthorization actor = support.actor(authentication, "iam:session:revoke");
        String reason = IamAdminCommandValidator.requireReason(command.reason(), "reason");
        String normalizedSessionId = IamAdminCommandValidator.requireId(sessionId, "sessionId");
        support.revokeSession(normalizedSessionId, actor.userId(), reason);
        support.audit(actor, request, "iam.session.revoked", SecurityEventCategory.SESSION,
                PermissionRiskLevel.HIGH, "SESSION", sessionId, sessionId,
                reason, null, IamAdminCommandValidator.json("revoked", "true"));
    }

    /** 撤销用户全部 Active Session 并返回实际撤销数。 */
    @Transactional
    public int revokeAllSessions(
            Authentication authentication, String userId,
            IamAdminService.Reason command, IamAdminService.RequestContext request) {
        MomJwtAuthorization actor = support.actor(authentication, "iam:session:revoke-all");
        support.requireUser(userId);
        String reason = IamAdminCommandValidator.requireReason(command.reason(), "reason");
        int count = support.revokeUserSessions(userId, actor.userId(), reason);
        support.audit(actor, request, "iam.user.sessions-revoked", SecurityEventCategory.SESSION,
                PermissionRiskLevel.HIGH, "USER", userId, null,
                reason, null, IamAdminCommandValidator.json("sessionCount", Integer.toString(count)));
        return count;
    }
}
