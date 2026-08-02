package io.github.chrisshi.mom.iam.application.admin.port;

import io.github.chrisshi.mom.iam.domain.role.IamRole;

import java.time.Instant;
import java.util.Optional;

/** PLATFORM_ADMIN 锁与有效人数事实 Port。 */
public interface IamPlatformAdministratorPort {
    Optional<IamRole> lockPlatformAdminRole();
    boolean isEffectivePlatformAdmin(String userId, Instant now);
    int countEffectivePlatformAdministrators(Instant now);
}
