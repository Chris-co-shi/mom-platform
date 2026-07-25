package io.github.chrisshi.mom.iam.bootstrap;

import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.iam.application.admin.model.IamAdminViews;
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

/**
 * 内置 {@code admin} 初始化事务服务。
 *
 * <p>服务先锁定 Flyway 创建的内置 {@code PLATFORM_ADMIN} 角色，再进行用户名冲突判断、密码编码、
 * 用户插入和角色分配。共享角色行锁同时串行化多实例 Bootstrap。已有系统账号只幂等跳过，绝不重置
 * 密码、状态、锁定计数、版本或角色；任一失败由 Spring 本地事务回滚。</p>
 */
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

    /**
     * 创建 Bootstrap 事务服务。
     *
     * @param repository 内置管理员精确仓储
     * @param auditContextExecutor 显式 SYSTEM 审计上下文执行器
     * @param properties Bootstrap 配置
     * @param passwordEncoder Spring Security 密码编码器
     * @param ids 安全 ID 生成器
     * @param environment Spring Profile 环境
     * @param clock UTC 时钟
     */
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

    /**
     * 初始化固定系统管理员。
     *
     * <p>方法幂等且不开放 HTTP 入口。密码只进入 PasswordEncoder 和参数化 INSERT，不进入日志、
     * 异常或返回值。</p>
     *
     * @throws IllegalStateException 配置不安全、内置角色缺失或用户名被普通账号占用时抛出
     */
    @Transactional
    public void initialize() {
        properties.validate(environment);
        auditContextExecutor.runAsSystem(SYSTEM_AUDIT_ACTOR, this::initializeWithinAuditContext);
    }

    /** 在显式 SYSTEM Actor 下完成所有数据库读取和写入。 */
    private void initializeWithinAuditContext() {
        IamAdminViews.RoleView role = repository.lockPlatformAdminRole()
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

    private static void requireUsablePlatformAdminRole(IamAdminViews.RoleView role) {
        if (!PLATFORM_ADMIN.equals(role.code())
                || !role.builtIn()
                || role.applicableUserType() != UserType.INTERNAL
                || role.status() != IamRecordStatus.ENABLED) {
            throw new IllegalStateException(
                    "Built-in PLATFORM_ADMIN role must be enabled and applicable to INTERNAL users");
        }
    }
}
