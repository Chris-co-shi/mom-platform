package io.github.chrisshi.mom.wms;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实 PostgreSQL 检查库存交易的原子性、幂等与并发扣减。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = MomWmsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.cloud.nacos.discovery.enabled=false", "mom.security.resource-server.enabled=false"})
class InventoryAccountingPostgresqlIT extends InventoryAccountingDatabaseContract {
    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.7-alpine"))
            .withDatabaseName("mom_platform").withUsername("mom").withPassword("mom");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRESQL.getJdbcUrl() + "&currentSchema=mom_wms");
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Test
    void balanceIndexTreatsNullDimensionsAsEqual() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid
                 WHERE c.relname='uk_wms_inventory_balance_dimension' AND i.indnullsnotdistinct
                """, Integer.class)).isEqualTo(1);
    }
}
