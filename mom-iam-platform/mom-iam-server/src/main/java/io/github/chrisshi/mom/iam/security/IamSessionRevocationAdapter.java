package io.github.chrisshi.mom.iam.security;

import io.github.chrisshi.mom.iam.application.admin.port.IamSessionRevocationPort;

/** Admin Application 到唯一 Session Token Service 的撤销 Adapter。 */
public final class IamSessionRevocationAdapter implements IamSessionRevocationPort {
    private final IamSessionTokenService sessions;

    public IamSessionRevocationAdapter(IamSessionTokenService sessions) {
        this.sessions = sessions;
    }

    @Override
    public void revoke(String sessionId, String actor, String reason) {
        sessions.revoke(sessionId, actor, reason);
    }
}
