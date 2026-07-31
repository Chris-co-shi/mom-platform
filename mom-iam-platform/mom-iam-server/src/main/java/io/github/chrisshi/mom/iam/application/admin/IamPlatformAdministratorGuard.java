package io.github.chrisshi.mom.iam.application.admin;

import io.github.chrisshi.mom.iam.application.admin.port.IamPlatformAdministratorPort;
import io.github.chrisshi.mom.iam.domain.policy.PlatformAdministratorRetentionPolicy;
import io.github.chrisshi.mom.iam.domain.role.IamRole;

import java.time.Clock;
import java.time.Instant;

/** 加载数据库锁定事实并调用最后管理员领域策略。 */
public final class IamPlatformAdministratorGuard {
    private final IamPlatformAdministratorPort administrators;
    private final PlatformAdministratorRetentionPolicy policy;
    private final Clock clock;

    public IamPlatformAdministratorGuard(
            IamPlatformAdministratorPort administrators,
            PlatformAdministratorRetentionPolicy policy,
            Clock clock) {
        this.administrators = administrators;
        this.policy = policy;
        this.clock = clock;
    }

    public void protectReduction(String userId) {
        IamRole platformAdministrator = administrators.lockPlatformAdminRole()
                .orElseThrow(() -> new IamAdminExceptions.Conflict(
                        "内置 PLATFORM_ADMIN 角色不存在"));
        Instant now = clock.instant();
        IamAdminFailures.fromDomain(() -> policy.requireReductionAllowed(
                platformAdministrator,
                administrators.isEffectivePlatformAdmin(userId, now),
                administrators.countEffectivePlatformAdministrators(now)));
    }
}
