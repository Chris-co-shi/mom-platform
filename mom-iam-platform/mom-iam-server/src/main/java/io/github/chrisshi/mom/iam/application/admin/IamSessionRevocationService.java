package io.github.chrisshi.mom.iam.application.admin;

import io.github.chrisshi.mom.iam.application.admin.port.IamSessionAdminQueryPort;
import io.github.chrisshi.mom.iam.application.admin.port.IamSessionRevocationPort;

/** 组合 Session 查询与权威撤销 Port，不定义事务边界。 */
public final class IamSessionRevocationService {
    private final IamSessionAdminQueryPort queries;
    private final IamSessionRevocationPort revocations;

    public IamSessionRevocationService(
            IamSessionAdminQueryPort queries, IamSessionRevocationPort revocations) {
        this.queries = queries;
        this.revocations = revocations;
    }

    public int revokeUserSessions(String userId, String actor, String reason) {
        int count = 0;
        for (String sessionId : queries.activeSessionIdsForUser(userId)) {
            revocations.revoke(sessionId, actor, reason);
            count++;
        }
        return count;
    }

    public int revokeClientSessions(String clientId, String actor, String reason) {
        int count = 0;
        for (String sessionId : queries.activeSessionIdsForClient(clientId)) {
            revocations.revoke(sessionId, actor, reason);
            count++;
        }
        return count;
    }

    public void revokeSession(String sessionId, String actor, String reason) {
        revocations.revoke(sessionId, actor, reason);
    }
}
