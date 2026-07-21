package io.github.chrisshi.mom.mdm;

import io.github.chrisshi.mom.core.security.AuditContextExecutor;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Transactional Outbox PostgreSQL 集成测试，并显式建立技术 SYSTEM Actor。 */
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
    private static final String SYSTEM_ACTOR = "mom-mdm-outbox-test";

    @Container
    private static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform").withUsername("mom").withPassword("mom")
            .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");

    @Autowired private MdmOutboxProbeService outboxProbeService;
    @Autowired private OutboxAppender outboxAppender;
    @Autowired private JdbcOutboxRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private AuditContextExecutor executor;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl()
                + "&currentSchema=" + SCHEMA + "&tcpKeepAlive=true&ApplicationName=mom-mdm-outbox-test");
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }

    @Test
    void domainWriteAndOutboxShouldCommitTogether() {
        MdmOutboxProbeService.MdmOutboxProbeResult result = createProbe("commit");
        assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from technical_data_probe where id = ?", Long.class, result.probeId()));
        assertEquals(SYSTEM_ACTOR, jdbcTemplate.queryForObject(
                "select created_by from technical_data_probe where id = ?", String.class, result.probeId()));
        assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from mom_outbox_event where event_id = ? and status = 'PENDING'",
                Long.class, result.eventId()));
    }

    @Test
    void domainWriteAndOutboxShouldRollbackTogether() {
        String probeKey = unique("rollback");
        String[] eventId = new String[1];
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> runAsSystem(() -> {
            template.executeWithoutResult(status -> {
                MdmOutboxProbeService.MdmOutboxProbeResult result = outboxProbeService.create(
                        probeKey, "rolled-back-value", "rollback-correlation", false);
                eventId[0] = result.eventId();
                throw new IllegalStateException("主动触发整体回滚");
            });
            return null;
        }));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "select count(*) from technical_data_probe where probe_key = ?", Long.class, probeKey));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "select count(*) from mom_outbox_event where event_id = ?", Long.class, eventId[0]));
    }

    @Test
    void appenderShouldRejectCallOutsideTransaction() {
        EventEnvelope event = new EventEnvelope(
                UUID.randomUUID().toString(), "mdm.technical-probe.created", 1,
                "MdmDataProbe", "probe-1", Instant.now(), "mom-mdm-server",
                "no-transaction", "{\"probeId\":\"probe-1\"}");
        assertThrows(IllegalStateException.class, () -> outboxAppender.append(event));
    }

    @Test
    void leasedClaimShouldPreventConcurrentDuplicateOwnership() {
        MdmOutboxProbeService.MdmOutboxProbeResult result = createProbe("lease");
        List<OutboxRecord> first = repository.claimAvailable("publisher-one", 100, Duration.ofSeconds(30));
        List<OutboxRecord> second = repository.claimAvailable("publisher-two", 100, Duration.ofSeconds(30));
        assertTrue(first.stream().anyMatch(record -> result.eventId().equals(record.eventId())));
        assertTrue(second.stream().noneMatch(record -> result.eventId().equals(record.eventId())));
        assertTrue(repository.markSent(result.eventId(), "publisher-one"));
        assertEquals(OutboxStatus.SENT, repository.findByEventId(result.eventId()).orElseThrow().status());
    }

    private MdmOutboxProbeService.MdmOutboxProbeResult createProbe(String prefix) {
        return runAsSystem(() -> outboxProbeService.create(
                unique(prefix), prefix + "-value", prefix + "-correlation", false));
    }

    private <T> T runAsSystem(Supplier<T> action) {
        return executor.runAsSystem(SYSTEM_ACTOR, action);
    }

    private static String unique(String prefix) { return prefix + "-" + UUID.randomUUID(); }
}
