package io.github.chrisshi.mom.iam.bootstrap;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.iam.application.admin.model.IamSecurityAuditEvent;
import io.github.chrisshi.mom.iam.application.admin.port.IamSecurityAuditSink;
import io.github.chrisshi.mom.iam.domain.role.IamRole;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.PermissionRiskLevel;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditActorType;
import io.github.chrisshi.mom.iam.domain.type.SecurityAuditResult;
import io.github.chrisshi.mom.iam.domain.type.SecurityEventCategory;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamBuiltInAdministratorRepository;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** 内置 admin 已存在但凭证丢失时的一次性本地恢复事务服务。 */
public class IamBuiltInAdministratorRecovery {
    static final String SYSTEM_ACTOR = "IAM_ADMIN_RECOVERY";
    static final String SYSTEM_AUDIT_ACTOR = "mom-iam-admin-recovery";
    private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    private final IamBuiltInAdministratorRepository repository;
    private final AuditContextExecutor auditContextExecutor;
    private final IamAdministratorRecoveryProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final IamSecurityAuditSink auditSink;
    private final IamSecureIdGenerator ids;
    private final Environment environment;
    private final Clock clock;

    public IamBuiltInAdministratorRecovery(
            IamBuiltInAdministratorRepository repository,
            AuditContextExecutor auditContextExecutor,
            IamAdministratorRecoveryProperties properties,
            PasswordEncoder passwordEncoder,
            IamSecurityAuditSink auditSink,
            IamSecureIdGenerator ids,
            Environment environment,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.auditContextExecutor = Objects.requireNonNull(auditContextExecutor, "auditContextExecutor");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 在单个 PostgreSQL 本地事务中锁定并恢复内置管理员凭证，同时追加 SYSTEM 安全审计。
     *
     * <p>Session/Refresh 撤销由启动 Runner 在该事务提交后执行，避免 Redis 或其他外部资源进入数据库事务。</p>
     */
    @Transactional
    public RecoveryResult recoverCredential() {
        properties.validate(environment);
        return auditContextExecutor.runAsSystem(
                SYSTEM_AUDIT_ACTOR, this::recoverWithinAuditContext);
    }

    private RecoveryResult recoverWithinAuditContext() {
        IamRole role = repository.lockPlatformAdminRole()
                .orElseThrow(() -> new IllegalStateException(
                        "Built-in PLATFORM_ADMIN role is required for IAM administrator recovery"));
        requireUsablePlatformAdminRole(role);

        IamUserMapper.BootstrapIdentity administrator = repository
                .lockByUsername(IamAdministratorBootstrapProperties.BUILT_IN_USERNAME)
                .orElseThrow(() -> new IllegalStateException(
                        "Built-in admin account does not exist; use IAM bootstrap instead"));
        requireRecoverableAdministrator(administrator);

        Instant now = clock.instant();
        if (!repository.isEffectivePlatformAdmin(administrator.id(), now)) {
            throw new IllegalStateException(
                    "Built-in admin account must retain an effective PLATFORM_ADMIN assignment");
        }
        repository.recoverAdministrator(
                administrator.id(),
                passwordEncoder.encode(properties.getPassword()),
                properties.isForcePasswordChange(),
                administrator.version(),
                SYSTEM_ACTOR,
                now);
        auditSink.append(new IamSecurityAuditEvent(
                ids.nextId(),
                "iam.admin.credential-recovered",
                SecurityEventCategory.ACCOUNT,
                PermissionRiskLevel.HIGH,
                SecurityAuditResult.SUCCESS,
                SecurityAuditActorType.SYSTEM,
                null,
                SYSTEM_AUDIT_ACTOR,
                "USER",
                administrator.id(),
                null,
                null,
                null,
                "administrator_recovery",
                null,
                "{\"forcePasswordChange\":" + properties.isForcePasswordChange() + "}",
                CorrelationContext.currentId(),
                now));
        return new RecoveryResult(administrator.id(), properties.isForcePasswordChange());
    }

    private static void requireRecoverableAdministrator(
            IamUserMapper.BootstrapIdentity administrator) {
        if (!IamAdministratorBootstrapProperties.BUILT_IN_USERNAME.equals(administrator.username())
                || administrator.userType() != UserType.INTERNAL
                || !administrator.systemAccount()
                || administrator.deleted()) {
            throw new IllegalStateException(
                    "Built-in admin must be an undeleted INTERNAL system account");
        }
    }

    private static void requireUsablePlatformAdminRole(IamRole role) {
        if (!PLATFORM_ADMIN.equals(role.code())
                || !role.builtIn()
                || role.applicableUserType() != UserType.INTERNAL
                || role.status() != IamRecordStatus.ENABLED) {
            throw new IllegalStateException(
                    "Built-in PLATFORM_ADMIN role must be enabled and applicable to INTERNAL users");
        }
    }

    /** 恢复事务提交后执行 Session 撤销所需的最小结果。 */
    public record RecoveryResult(String userId, boolean forcePasswordChange) {
    }
}
