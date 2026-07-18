package io.github.chrisshi.mom.outbox.autoconfigure;

import io.github.chrisshi.mom.messaging.event.EventTransport;
import io.github.chrisshi.mom.outbox.application.InboxDeduplicator;
import io.github.chrisshi.mom.outbox.application.OutboxAppender;
import io.github.chrisshi.mom.outbox.application.OutboxPublisher;
import io.github.chrisshi.mom.outbox.config.OutboxPublisherProperties;
import io.github.chrisshi.mom.outbox.persistence.JdbcOutboxRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;

/**
 * MOM Outbox/Inbox 自动配置。
 *
 * <p>该配置只创建 JDBC 基础设施对象，不创建数据库表；每个领域服务必须通过自己的 Flyway 迁移管理
 * {@code mom_outbox_event} 或 {@code mom_inbox_event}。所有 Bean 使用应用唯一 DataSource 对应的事务管理器，
 * 不声明第二数据源或第二连接池。</p>
 *
 * <p>Outbox 追加器和 Inbox 幂等器在 JDBC 可用时创建。定时发布器默认关闭，只有显式设置
 * {@code mom.outbox.publisher.enabled=true} 且存在 {@link EventTransport} 时才启用调度。</p>
 */
@AutoConfiguration
@ConditionalOnClass(JdbcTemplate.class)
@ConditionalOnBean({JdbcTemplate.class, PlatformTransactionManager.class})
@EnableConfigurationProperties(OutboxPublisherProperties.class)
public class MomOutboxAutoConfiguration {

    /**
     * 创建 PostgreSQL Outbox 仓储。
     *
     * @param jdbcTemplate 当前服务 JDBC 模板
     * @param transactionManager 当前服务唯一本地事务管理器
     * @return 租约式 Outbox 仓储
     */
    @Bean
    @ConditionalOnMissingBean
    JdbcOutboxRepository momJdbcOutboxRepository(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        return new JdbcOutboxRepository(
                jdbcTemplate,
                new TransactionTemplate(transactionManager));
    }

    /**
     * 创建强制要求活动事务的 Outbox 追加端口。
     *
     * @param repository Outbox 仓储
     * @return Outbox 追加端口
     */
    @Bean
    @ConditionalOnMissingBean
    OutboxAppender momOutboxAppender(JdbcOutboxRepository repository) {
        return new OutboxAppender(repository);
    }

    /**
     * 创建 Inbox 消费幂等执行器。
     *
     * @param jdbcTemplate 当前服务 JDBC 模板
     * @param transactionManager 当前服务本地事务管理器
     * @return Inbox 幂等执行器
     */
    @Bean
    @ConditionalOnMissingBean
    InboxDeduplicator momInboxDeduplicator(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        return new InboxDeduplicator(
                jdbcTemplate,
                new TransactionTemplate(transactionManager));
    }

    /**
     * 创建启用后的租约式 Outbox 发布器。
     *
     * @param repository Outbox 仓储
     * @param transport Spring Cloud Stream 事件传输端口
     * @param properties 发布参数
     * @param clock 平台 UTC 时钟
     * @param environment 应用环境，用于构造稳定可诊断的租约前缀
     * @return Outbox 定时发布器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "mom.outbox.publisher",
            name = "enabled",
            havingValue = "true")
    OutboxPublisher momOutboxPublisher(
            JdbcOutboxRepository repository,
            EventTransport transport,
            OutboxPublisherProperties properties,
            Clock clock,
            Environment environment) {
        String applicationName = environment.getProperty(
                "spring.application.name",
                "unknown-application");
        String leaseOwner = applicationName + ":" + UUID.randomUUID();
        return new OutboxPublisher(repository, transport, properties, clock, leaseOwner);
    }

    /**
     * 仅在发布器启用时打开 Spring Scheduling，避免普通依赖方无意创建调度线程。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(
            prefix = "mom.outbox.publisher",
            name = "enabled",
            havingValue = "true")
    static class OutboxSchedulingConfiguration {
    }
}
