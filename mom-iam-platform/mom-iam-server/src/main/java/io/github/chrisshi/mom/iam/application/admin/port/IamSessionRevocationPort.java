package io.github.chrisshi.mom.iam.application.admin.port;

/** Session 权威撤销 Port。 */
public interface IamSessionRevocationPort {
    void revoke(String sessionId, String actor, String reason);
}
