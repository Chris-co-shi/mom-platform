package io.github.chrisshi.mom.iam.bootstrap;

import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.iam.domain.role.IamRole;
import io.github.chrisshi.mom.iam.domain.type.IamRecordStatus;
import io.github.chrisshi.mom.iam.domain.type.UserType;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.repository.IamBuiltInAdministratorRepository;
import io.github.chrisshi.mom.iam.security.IamSecureIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** 内置 admin 初始化事务服务。 */
public class IamBuiltInAdministratorBootstrap {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(IamBuiltInAdministratorBootstrap.class);
    private static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    private static final String SYSTEM_ACTOR = "IAM_BOOTSTRAP";
    private static final String SYSTEM_AUDIT_ACTOR = "mom-iam-bootstrap";

    private final IamBuiltInAdministratorRepository repository;
    private final AuditContextExecutor auditContextExecutor;
    private final IamAdministratorBootstrapProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final IamSecureIdGenerator ids;
    private final Environment environment;
    private final Clock clock;

    public IamBuiltInAdministratorBootstrap(
            IamBuiltInAdministratorRepository repository,
            AuditContextExecutor auditContextExecutor,
            IamAdministratorBootstrapProperties properties,
            PasswordEncoder passwordEncoder,
            IamSecureIdGenerator ids,
            Environment environment,
            Clock clock) {
        this.repository = repository;
        this.auditContextExecutor = auditContextExecutor;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.ids = ids;
        this.environment = environment;
        this.clock = clock;
    }

    @Transactional
    public void initialize() {
        properties.validate(environment);
        auditContextExecutor.runAsSystem(
                SYSTEM_AUDIT_ACTOR, this::initializeWithinAuditContext);
    }

    private void initializeWithinAuditContext() {
        IamRole role = repository.lockPlatformAdminRole()
                .orElseThrow(() -> new IllegalStateException(
                        "Built-in PLATFORM_ADMIN role is required for IAM bootstrap"));
        requireUsablePlatformAdminRole(role);

        IamUserMapper.BootstrapIdentity existing = repository
                .findByUsername(IamAdministratorBootstrapProperties.BUILT_IN_USERNAME)
                .orElse(null);
        if (existing != null) {
            if (!existing.systemAccount()) {
                throw new IllegalStateException(
                        "Username admin is already occupied by a non-system account");
            }
            LOGGER.info("IAM built-in administrator already exists; bootstrap skipped");
            return;
        }

        Instant now = clock.instant();
        String userId = ids.nextId();
        repository.insertAdministrator(
                userId,
                IamAdministratorBootstrapProperties.BUILT_IN_USERNAME,
                passwordEncoder.encode(properties.getPassword()),
                properties.getDisplayName().trim(),
                SYSTEM_ACTOR,
                now);
        repository.assignPlatformAdmin(
                ids.nextId(), userId, role.id(), SYSTEM_ACTOR, now);
        LOGGER.info("IAM built-in administrator initialized: username=admin");
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
}
