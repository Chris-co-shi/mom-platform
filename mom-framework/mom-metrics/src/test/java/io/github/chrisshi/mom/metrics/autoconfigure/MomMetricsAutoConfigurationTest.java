package io.github.chrisshi.mom.metrics.autoconfigure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link MomMetricsAutoConfiguration} 公共标签契约测试。
 */
class MomMetricsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MomMetricsAutoConfiguration.class));

    /**
     * 应用名和部署环境必须成为所有 Meter 的稳定公共标签。
     */
    @Test
    void shouldApplyApplicationAndEnvironmentCommonTags() {
        contextRunner
                .withPropertyValues(
                        "spring.application.name=mom-test-service",
                        "mom.metrics.environment=ci")
                .run(context -> {
                    @SuppressWarnings("unchecked")
                    MeterRegistryCustomizer<MeterRegistry> customizer =
                            (MeterRegistryCustomizer<MeterRegistry>) context.getBean(
                                    "momMetricsCommonTagsCustomizer",
                                    MeterRegistryCustomizer.class);
                    SimpleMeterRegistry registry = new SimpleMeterRegistry();
                    customizer.customize(registry);

                    Counter counter = registry.counter("mom.test.counter");
                    assertEquals("mom-test-service", counter.getId().getTag("application"));
                    assertEquals("ci", counter.getId().getTag("environment"));
                });
    }
}
