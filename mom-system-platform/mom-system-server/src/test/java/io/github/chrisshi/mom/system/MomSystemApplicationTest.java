package io.github.chrisshi.mom.system;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System S13 运行时的基础设施失败策略测试。
 *
 * <p>测试使用非 Web ApplicationContext，不绑定端口、不访问网络，也不提供数据库连接。S13 的参数权威
 * 必须是 PostgreSQL，因此缺少数据源配置时 Context 必须 Fail Closed；真实成功启动由 PostgreSQL
 * Testcontainers IT 验证。本测试不排除自动配置或伪造连接信息。</p>
 */
class MomSystemApplicationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MomSystemApplication.class)
            .withPropertyValues("spring.cloud.nacos.discovery.enabled=false");

    /**
     * 验证缺少 PostgreSQL 配置时启动明确失败，不退化为空服务或内存参数源。
     */
    @Test
    void shouldFailClosedWithoutPostgresqlConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .hasRootCauseMessage("Failed to determine a suitable driver class");
        });
    }
}
