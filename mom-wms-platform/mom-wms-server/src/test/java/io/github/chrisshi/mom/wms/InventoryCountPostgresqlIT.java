package io.github.chrisshi.mom.wms;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 用真实 PostgreSQL 验证盘点快照、整仓保护、整单调整与失败回滚。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes=MomWmsApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE,
        properties={"spring.cloud.nacos.discovery.enabled=false","mom.security.resource-server.enabled=false"})
class InventoryCountPostgresqlIT extends InventoryCountDatabaseContract {
    @Container static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:17.7-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->PG.getJdbcUrl()+"&currentSchema=mom_wms");
        registry.add("spring.datasource.username",PG::getUsername);
        registry.add("spring.datasource.password",PG::getPassword);
    }
}
