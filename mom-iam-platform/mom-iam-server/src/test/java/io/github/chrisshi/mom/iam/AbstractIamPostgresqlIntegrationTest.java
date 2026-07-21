package io.github.chrisshi.mom.iam;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** IAM PostgreSQL 集成测试公共 Spring 配置与容器工厂。 */
@SpringBootTest(
        classes = MomIamApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration,"
                        + "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration"
        })
abstract class AbstractIamPostgresqlIntegrationTest {
    protected static final String SCHEMA = "mom_iam";
    protected static final String APPLICATION_NAME = "mom-iam-server";

    /**
     * 为每个测试类创建独立 PostgreSQL 17 容器，避免一个类结束后停止共享容器导致连接池指向失效端口。
     *
     * @return 尚未启动的 PostgreSQL Testcontainers 容器
     */
    protected static PostgreSQLContainer newPostgresqlContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17.7-alpine"))
                .withDatabaseName("mom_platform")
                .withUsername("mom")
                .withPassword("mom")
                .withCommand("postgres", "-c", "fsync=off", "-c", "timezone=Asia/Tokyo");
    }

    /**
     * 把指定测试类自己的动态端口、凭证和 IAM Schema 注入 Spring Context。
     *
     * @param registry Spring 动态属性注册器
     * @param postgresql 当前测试类独占的 PostgreSQL 容器
     */
    protected static void registerDatabaseProperties(
            DynamicPropertyRegistry registry,
            PostgreSQLContainer postgresql) {
        registry.add("spring.datasource.url", () -> postgresql.getJdbcUrl()
                + "&currentSchema=" + SCHEMA
                + "&tcpKeepAlive=true&ApplicationName=" + APPLICATION_NAME);
        registry.add("spring.datasource.username", postgresql::getUsername);
        registry.add("spring.datasource.password", postgresql::getPassword);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
    }
}
