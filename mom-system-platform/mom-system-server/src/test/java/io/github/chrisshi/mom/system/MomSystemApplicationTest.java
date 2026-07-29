package io.github.chrisshi.mom.system;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * System 空运行时的轻量启动测试。
 *
 * <p>测试使用非 Web ApplicationContext，不绑定端口、不访问网络，也不启动数据库、中间件或容器。
 * Context 创建失败时直接失败，不通过排除自动配置或伪造连接信息掩盖依赖问题。</p>
 */
class MomSystemApplicationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MomSystemApplication.class)
            .withPropertyValues("spring.cloud.nacos.discovery.enabled=false");

    /**
     * 验证启动类可以创建最小 Context，且类路径没有触发数据源。
     */
    @Test
    void shouldStartWithoutExternalInfrastructure() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MomSystemApplication.class);
            assertThat(context.getBeanNamesForType(DataSource.class)).isEmpty();
        });
    }
}
