package io.github.chrisshi.mom.wms;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;

/** PostgreSQL 真实 SQL、不可变快照、分录分页与资源服务授权契约。 */
@Testcontainers(disabledWithoutDocker = true)
@org.springframework.boot.test.context.SpringBootTest(classes = MomWmsApplication.class, webEnvironment = MOCK,
        properties = {"spring.cloud.nacos.discovery.enabled=false", "mom.security.resource-server.enabled=true",
                "management.health.redis.enabled=false"})
class InventoryHistoryPostgresqlIT extends InventoryHistoryDatabaseContract {
    @Container static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17.7-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> PG.getJdbcUrl() + "&currentSchema=mom_wms");
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.flyway.schemas", () -> "mom_wms");
        registry.add("spring.flyway.default-schema", () -> "mom_wms");
    }
}
