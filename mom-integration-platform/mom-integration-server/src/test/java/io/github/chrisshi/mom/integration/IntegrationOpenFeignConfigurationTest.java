package io.github.chrisshi.mom.integration;

import feign.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.FeignClientProperties;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration 到 MDM 的 OpenFeign 有限超时与日志级别绑定测试。
 *
 * <p>测试只读取当前模块的环境中立配置，不发起网络请求、不启动 Nacos，也不改变 Retryer、凭证传播或
 * ErrorDecoder。它确保配置键能被锁定版本的 Spring Cloud OpenFeign 5.0.x 类型安全属性实际绑定。</p>
 */
class IntegrationOpenFeignConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(FeignPropertiesConfiguration.class);

    /** 正式 Client 必须绑定有限 connect/read timeout，且 Base 日志不得为 FULL。 */
    @Test
    void mdmClientMustBindFiniteTimeoutsAndNonFullLogging() {
        runner.run(context -> {
            FeignClientProperties properties = context.getBean(FeignClientProperties.class);
            FeignClientProperties.FeignClientConfiguration client =
                    properties.getConfig().get("mom-mdm-server");

            assertThat(client).isNotNull();
            assertThat(client.getConnectTimeout()).isEqualTo(2000);
            assertThat(client.getReadTimeout()).isEqualTo(3000);
            assertThat(client.getLoggerLevel()).isEqualTo(Logger.Level.BASIC);
        });
    }

    /** 只启用 OpenFeign 属性绑定，不注册 Client 或任何外部基础设施。 */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FeignClientProperties.class)
    static class FeignPropertiesConfiguration {
    }
}
