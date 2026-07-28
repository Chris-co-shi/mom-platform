package io.github.chrisshi.mom.iam.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/** IAM 正式基础配置的本机地址、关闭开关和空安全凭据默认值绑定测试。 */
class IamRuntimeSecureDefaultsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void baseConfigurationMustBeEnvironmentNeutralAndSecureByDefault() {
        runner.run(context -> {
            Environment environment = context.getEnvironment();
            assertThat(environment.getProperty("spring.datasource.url"))
                    .contains("127.0.0.1")
                    .contains("/mom_platform?")
                    .contains("currentSchema=mom_iam");
            assertThat(environment.getProperty("spring.datasource.password")).isEmpty();
            assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("127.0.0.1");
            assertThat(environment.getProperty("spring.data.redis.password")).isEmpty();
            assertThat(environment.getProperty("spring.cloud.nacos.discovery.enabled", Boolean.class))
                    .isFalse();
            assertThat(environment.getProperty("spring.cloud.nacos.discovery.password")).isEmpty();
            assertThat(environment.getProperty("mom.iam.bootstrap.enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty("mom.iam.bootstrap.password")).isEmpty();
            assertThat(environment.getProperty("mom.iam.authorization.key.private-key-location"))
                    .isEmpty();
            assertThat(environment.getProperty("mom.iam.authorization.key.public-key-location"))
                    .isEmpty();
            assertThat(environment.getProperty(
                    "mom.iam.authorization.key.allow-test-key", Boolean.class)).isFalse();
            assertThat(environment.getProperty("mom.iam.session.hmac-pepper")).isEmpty();
            assertThat(environment.getProperty("mom.iam.session.allow-local-pepper", Boolean.class))
                    .isFalse();
            assertThat(environment.getProperty(
                    "management.otlp.metrics.export.enabled", Boolean.class)).isFalse();
            assertThat(environment.getProperty(
                    "management.tracing.export.otlp.enabled", Boolean.class)).isFalse();
        });
    }
}
