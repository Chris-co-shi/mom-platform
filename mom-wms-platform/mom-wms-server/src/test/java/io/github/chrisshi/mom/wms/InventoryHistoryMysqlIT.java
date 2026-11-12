package io.github.chrisshi.mom.wms;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** 在 MySQL 8.4 运行对应的 WMS 持久化行为验证。 */
@Testcontainers
@SpringBootTest(classes = MomWmsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"spring.cloud.nacos.discovery.enabled=false", "mom.security.resource-server.enabled=true", "management.health.redis.enabled=false", "MOM_DATABASE_VENDOR=mysql", "POSTGRES_URL=jdbc:postgresql://127.0.0.1:1/unexpected_postgresql"})
class InventoryHistoryMysqlIT extends InventoryHistoryDatabaseContract {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4").withDatabaseName("mom_wms")
            .withCommand("--log-bin-trust-function-creators=ON");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("MYSQL_URL", MYSQL::getJdbcUrl);
        registry.add("MYSQL_USERNAME", MYSQL::getUsername);
        registry.add("MYSQL_PASSWORD", MYSQL::getPassword);
    }
}
