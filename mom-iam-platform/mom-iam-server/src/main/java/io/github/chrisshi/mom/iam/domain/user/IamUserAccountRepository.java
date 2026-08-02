package io.github.chrisshi.mom.iam.domain.user;

import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;

import java.time.Instant;
import java.util.Optional;

/** IAM User 聚合的框架无关持久化 Port。 */
public interface IamUserAccountRepository {

    Optional<IamUserAccount> findById(String userId);

    Optional<IamUserAccount> lockById(String userId);

    void create(IamUserAccount account, String passwordHash, String actor, Instant now);

    void updateDisplayName(
            String userId, String displayName, long expectedVersion, String actor, Instant now);

    void updateStatus(
            String userId, IamRecordStatus status, long expectedVersion, String actor, Instant now);

    void unlock(String userId, long expectedVersion, String actor, Instant now);

    void resetCredential(
            String userId, String passwordHash, long expectedVersion, String actor, Instant now);

    void logicalDelete(String userId, long expectedVersion, String actor, Instant now);
}
