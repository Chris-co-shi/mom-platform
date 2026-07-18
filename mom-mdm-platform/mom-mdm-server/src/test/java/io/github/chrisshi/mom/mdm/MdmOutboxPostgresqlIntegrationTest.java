package io.github.chrisshi.mom.mdm;

import io.github.chrisshi.mom.mdm.application.MdmOutboxProbeService;
import io.github.chrisshi.mom.messaging.event.EventEnvelope;
import io.github.chrisshi.mom.outbox.application.OutboxAppender;
import io.github.chrisshi.mom.outbox.model.OutboxRecord;
import io.github.chrisshi.mom.outbox.model.OutboxStatus;
import io.github.chrisshi.mom.outbox.persistence.JdbcOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MDM Transactional Outbox 真实 PostgreSQL 集成测试。
 *
 * <p>测试验证 P01-S05 的核心一致性边界，而不连接 RocketMQ：领域技术记录和 Outbox 必须同事务提交或回滚；
 * 无事务调用追加端口必须失败；多个发布实例通过 PostgreSQL {@code SKIP LOCKED} 和租约只能领取一次；只有
 * 持有当前租约的实例才能把记录标记为 SENT。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = MomMdmApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "mom.outbox.publisher.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"
        })
class MdmOutboxPostgresqlIntegrationTest {

    private static final String SCHEMA = "mom_mdm";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform")
            .withUsername("mom")
            .withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");

    @Autowired
    private MdmOutboxProbeService outboxProbeService;

    @Autowired
    private OutboxAppender outboxAppender;

    @Autowired
    private JdbcOutboxRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 将 Testcontainers PostgreSQL 连接和 MDM Schema 注入应用。
     *
     * @param registry Spring 测试动态属性注册器
     */
    @DynamicPropertySource
    static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl()
                + "&currentSchema=" + SCHEMA
                + "&tcpKeepAlive=true"
                + "&ApplicationName=mom-mdm-outbox-test");
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    /**
     * 验证业务技术记录和 Outbox 在同一事务中一起提交，并使用同一事件身份。
     */
    @Test
    void domainWriteAndOutboxShouldCommitTogether() {
        String probeKey = uniqueKey("commit");

        MdmOutboxProbeService.MdmOutboxProbeResult result = outboxProbeService.create(
                probeKey,
                "committed-value",
                "p01-s05-commit-correlation",
                false);

        assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from technical_data_probe where id = ?",
                Long.class,
                result.probeId()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from mom_outbox_event where event_id = ? and status = 'PENDING'",
                Long.class,
                result.eventId()));
    }

    /**
     * 验证外层事务失败时，已完成的业务 INSERT 与 Outbox INSERT 都被回滚。
     */
    @Test
    void domainWriteAndOutboxShouldRollbackTogether() {
        String probeKey = uniqueKey("rollback");
        String[] eventId = new String[1];
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> template.executeWithoutResult(status -> {
            MdmOutboxProbeService.MdmOutboxProbeResult result = outboxProbeService.create(
                    probeKey,
                    "rolled-back-value",
                    "p01-s05-rollback-correlation",
                    false);
            eventId[0] = result.eventId();
            throw new IllegalStateException("主动触发业务与 Outbox 整体回滚");
        }));

        assertEquals(0L, jdbcTemplate.queryForObject(
                "select count(*) from technical_data_probe where probe_key = ?",
                Long.class,
                probeKey));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "select count(*) from mom_outbox_event where event_id = ?",
                Long.class,
                eventId[0]));
    }

    /**
     * 验证调用方无法在没有活动本地事务时绕过 Outbox 原子性约束。
     */
    @Test
    void appenderShouldRejectCallOutsideTransaction() {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID().toString(),
                "mdm.technical-probe.created",
                1,
                "MdmDataProbe",
                "probe-1",
                Instant.now(),
                "mom-mdm-server",
                "p01-s05-no-transaction",
                "{\"probeId\":\"probe-1\"}");

        assertThrows(IllegalStateException.class, () -> outboxAppender.append(event));
    }

    /**
     * 验证租约领取互斥、重复领取隔离以及持有者条件更新。
     *
     * <p>同一测试上下文中的其他测试可能已经留下待发布记录，因此首个发布器批量领取所有可用记录，再只断言
     * 本测试事件包含在其租约中，避免测试方法执行顺序影响结论。</p>
     */
    @Test
    void leasedClaimShouldPreventConcurrentDuplicateOwnership() {
        MdmOutboxProbeService.MdmOutboxProbeResult result = outboxProbeService.create(
                uniqueKey("lease"),
                "lease-value",
                "p01-s05-lease-correlation",
                false);

        List<OutboxRecord> firstClaim = repository.claimAvailable(
                "publisher-one",
                100,
                Duration.ofSeconds(30));
        List<OutboxRecord> secondClaim = repository.claimAvailable(
                "publisher-two",
                100,
                Duration.ofSeconds(30));

        assertTrue(firstClaim.stream().anyMatch(record ->
                result.eventId().equals(record.eventId())));
        assertTrue(secondClaim.stream().noneMatch(record ->
                result.eventId().equals(record.eventId())));
        assertTrue(repository.markSent(result.eventId(), "publisher-one"));
        assertEquals(OutboxStatus.SENT, repository.findByEventId(result.eventId())
                .orElseThrow()
                .status());
    }

    private static String uniqueKey(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
