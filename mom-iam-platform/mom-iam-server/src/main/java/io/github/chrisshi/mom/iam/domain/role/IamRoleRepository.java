package io.github.chrisshi.mom.iam.domain.role;

import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** IAM Role 聚合的框架无关持久化 Port。 */
public interface IamRoleRepository {

    Optional<IamRole> lockById(String roleId);

    List<IamRole> findByIds(Collection<String> roleIds);

    void create(IamRole role, String actor, Instant now);

    void update(
            String roleId,
            String name,
            String description,
            IamRecordStatus status,
            long expectedVersion,
            String actor,
            Instant now);
}
