package io.github.chrisshi.mom.iam;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * IAM PostgreSQL 集成测试公共 Spring 配置与容器工厂。
 *
 * <p>测试使用 Servlet Mock 上下文加载完整 IAM 配置，确保各条安全链获得真实的
 * {@code HttpSecurity} 构建器；不会启动监听端口，也不会改变生产认证行为。数据库仍由每个测试类
 * 独占的 Testcontainers PostgreSQL 提供，避免跨类共享容器生命周期。</p>
 */
@SpringBootTest(
        classes = MomIamApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false"
        })
abstract class AbstractIamPostgresqlIntegrationTest {
    protected static final String SCHEMA = "mom_iam";
    protected static final String APPLICATION_NAME = "mom-iam-server";

    /** 隔离应用启动时的 Public Client 注册副作用，保持迁移目录测试只验证数据库基线。 */
    @MockitoBean(name = "iamRegisteredClientInitializer")
    ApplicationRunner registeredClientInitializer;

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
