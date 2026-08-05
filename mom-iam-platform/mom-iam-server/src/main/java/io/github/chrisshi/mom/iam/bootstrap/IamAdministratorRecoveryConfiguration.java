package io.github.chrisshi.mom.iam.bootstrap;

import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.iam.application.admin.IamSessionRevocationService;
import io.github.chrisshi.mom.iam.application.admin.port.IamIdentifierGenerator;
import io.github.chrisshi.mom.iam.application.admin.port.IamSecurityAuditSink;
import io.github.chrisshi.mom.iam.application.recovery.IamAdministratorRecoveryApplicationService;
import io.github.chrisshi.mom.iam.application.recovery.IamAdministratorRecoveryPort;
import io.github.chrisshi.mom.iam.configuration.IamAdminConfiguration;
import io.github.chrisshi.mom.iam.infrastructure.persistence.mapper.IamUserSessionMapper;
import io.github.chrisshi.mom.iam.infrastructure.persistence.query.MybatisIamSessionAdminQuery;
import io.github.chrisshi.mom.iam.security.IamSessionRevocationAdapter;
import io.github.chrisshi.mom.iam.security.IamSessionTokenService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/**
 * IAM 内置管理员一次性恢复自动配置。
 *
 * <p>该配置只负责把既有 Port、PasswordEncoder、Session 撤销和启动 Runner 装配到恢复用例。只有显式
 * {@code mom.iam.recovery.enabled=true} 才创建写入能力；依赖缺失、生产 Profile、Bootstrap 冲突或配置
 * 不完整都会阻止应用启动。恢复完成后运维方必须停止实例、移除全部恢复环境变量并正常重启。</p>
 */
@AutoConfiguration(after = {
        IamAdministratorBootstrapConfiguration.class,
        IamAdminConfiguration.class
})
@ConditionalOnBean(SqlSessionFactory.class)
@EnableConfigurationProperties({
        IamAdministratorRecoveryProperties.class,
        IamAdministratorBootstrapProperties.class
})
public class IamAdministratorRecoveryConfiguration {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(IamAdministratorRecoveryConfiguration.class);
    private static final String SYSTEM_AUDIT_ACTOR = "mom-iam-admin-recovery";

    /**
     * 装配恢复 Application Service；禁用恢复时不创建该写能力。
     *
     * @return 只依赖应用 Port 的恢复用例
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "mom.iam.recovery", name = "enabled", havingValue = "true")
    IamAdministratorRecoveryApplicationService iamAdministratorRecoveryApplicationService(
            IamAdministratorRecoveryPort administrators,
            PasswordEncoder passwordEncoder,
            IamUserSessionMapper sessionMapper,
            IamSessionTokenService sessions,
            IamSecurityAuditSink audits,
            IamIdentifierGenerator ids,
            Clock clock) {
        IamSessionRevocationService revocations = new IamSessionRevocationService(
                new MybatisIamSessionAdminQuery(sessionMapper),
                new IamSessionRevocationAdapter(sessions));
        return new IamAdministratorRecoveryApplicationService(
                administrators, passwordEncoder::encode, revocations, audits, ids, clock);
    }

    /**
     * 注册单次启动恢复 Runner。
     *
     * <p>Bean 创建阶段完成全部配置校验；执行阶段建立稳定 SYSTEM 数据审计主体，并在 finally 中清空
     * Properties 对临时凭据的引用。日志仅记录用户名和撤销计数，不记录 Secret 或摘要。</p>
     *
     * @return 启动期一次性恢复任务
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "mom.iam.recovery", name = "enabled", havingValue = "true")
    ApplicationRunner iamAdministratorRecoveryRunner(
            IamAdministratorRecoveryApplicationService recovery,
            IamAdministratorRecoveryProperties properties,
            IamAdministratorBootstrapProperties bootstrap,
            AuditContextExecutor auditContextExecutor,
            Environment environment) {
        properties.validate(environment, bootstrap.isEnabled());
        return arguments -> {
            String temporaryCredential = properties.getPassword();
            try {
                auditContextExecutor.runAsSystem(SYSTEM_AUDIT_ACTOR, () -> {
                    var result = recovery.recover(temporaryCredential);
                    LOGGER.warn(
                            "IAM built-in administrator recovery completed; "
                                    + "remove recovery environment variables and restart; "
                                    + "username=admin, revokedSessions={}",
                            result.revokedSessions());
                });
            } finally {
                properties.setPassword("");
            }
        };
    }
}
