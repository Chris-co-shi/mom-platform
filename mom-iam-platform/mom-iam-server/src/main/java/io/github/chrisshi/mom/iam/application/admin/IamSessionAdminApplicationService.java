package io.github.chrisshi.mom.iam.application.admin;

import io.github.chrisshi.mom.iam.application.admin.model.IamAdminActor;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminCommands;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminRequestContext;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
import io.github.chrisshi.mom.iam.application.admin.port.IamSessionAdminQueryPort;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;
import io.github.chrisshi.mom.iam.domain.user.IamUserAccountRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** IAM Session 管理用例。 */
public class IamSessionAdminApplicationService {
    private final IamSessionAdminQueryPort queries;
    private final IamUserAccountRepository users;
    private final IamSessionRevocationService revocations;
    private final IamAdminAuditService audits;

    public IamSessionAdminApplicationService(
            IamSessionAdminQueryPort queries,
            IamUserAccountRepository users,
            IamSessionRevocationService revocations,
            IamAdminAuditService audits) {
        this.queries = queries;
        this.users = users;
        this.revocations = revocations;
        this.audits = audits;
    }

    public List<IamAdminViews.SessionView> listSessions(
            IamAdminActor actor, String userId, String status, int limit, int offset) {
        actor.requirePermission("iam:session:read");
        return queries.listSessions(
                userId, status,
                IamAdminCommandValidator.pageSize(limit),
                IamAdminCommandValidator.pageOffset(offset));
    }

    @Transactional
    public void revokeSession(
            IamAdminActor actor,
            String sessionId,
            IamAdminCommands.Reason command,
            IamAdminRequestContext request) {
        actor.requirePermission("iam:session:revoke");
        String reason = IamAdminCommandValidator.requireReason(command.reason(), "reason");
        String normalizedSessionId =
                IamAdminCommandValidator.requireId(sessionId, "sessionId");
        revocations.revokeSession(normalizedSessionId, actor.userId(), reason);
        audits.record(
                actor, request, "iam.session.revoked", SecurityEventCategory.SESSION,
                PermissionRiskLevel.HIGH, "SESSION", normalizedSessionId,
                normalizedSessionId, reason, null,
                IamAdminCommandValidator.json("revoked", "true"));
    }

    @Transactional
    public int revokeAllSessions(
            IamAdminActor actor,
            String userId,
            IamAdminCommands.Reason command,
            IamAdminRequestContext request) {
        actor.requirePermission("iam:session:revoke-all");
        String normalizedUserId = IamAdminCommandValidator.requireId(userId, "userId");
        if (users.findById(normalizedUserId).isEmpty()) {
            throw new IamAdminExceptions.NotFound("用户不存在");
        }
        String reason = IamAdminCommandValidator.requireReason(command.reason(), "reason");
        int count = revocations.revokeUserSessions(
                normalizedUserId, actor.userId(), reason);
        audits.record(
                actor, request, "iam.user.sessions-revoked", SecurityEventCategory.SESSION,
                PermissionRiskLevel.HIGH, "USER", normalizedUserId, null,
                reason, null,
                IamAdminCommandValidator.json("sessionCount", Integer.toString(count)));
        return count;
    }
}
