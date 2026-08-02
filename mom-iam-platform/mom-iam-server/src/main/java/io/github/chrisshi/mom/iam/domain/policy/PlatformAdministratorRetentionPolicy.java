package io.github.chrisshi.mom.iam.domain.policy;

import io.github.chrisshi.mom.iam.domain.exception.IamDomainConflictException;
import io.github.chrisshi.mom.iam.domain.role.IamRole;

/** 至少保留一个有效 PLATFORM_ADMIN 的领域策略。 */
public final class PlatformAdministratorRetentionPolicy {

    public void requireReductionAllowed(
            IamRole lockedPlatformAdministratorRole,
            boolean targetIsEffectivePlatformAdministrator,
            int effectivePlatformAdministratorCount) {
        lockedPlatformAdministratorRole.requirePlatformAdministratorInvariant();
        if (targetIsEffectivePlatformAdministrator && effectivePlatformAdministratorCount <= 1) {
            throw new IamDomainConflictException("系统必须至少保留一个有效 PLATFORM_ADMIN");
        }
    }
}
