package io.github.chrisshi.mom.metrics.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * MOM Micrometer 指标治理自动配置。
 *
 * <p>配置为所有 Meter 增加稳定的 {@code application} 与 {@code environment} 公共标签，使 Prometheus、
 * Grafana 和告警规则能够按服务与环境聚合。公共标签只允许低基数部署属性，禁止加入实例随机值、用户、
 * 业务单号、事件 ID、Trace ID 或 Token 信息。</p>
 *
 * <p>该配置只定制 Spring Boot 已创建的 {@link MeterRegistry}，不自行创建 Registry 或网络 Exporter。
 * Prometheus 不可用时应用仍继续记录本地 Meter，抓取失败不会改变业务请求、事务或消息状态。</p>
 */
@AutoConfiguration(afterName = "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration")
@ConditionalOnClass({MeterRegistry.class, MeterRegistryCustomizer.class})
public class MomMetricsAutoConfiguration {

    /**
     * 创建平台公共标签定制器。
     *
     * @param environment Spring 配置环境
     * @return 为每个 MeterRegistry 增加服务与环境标签的定制器
     */
    @Bean(name = "momMetricsCommonTagsCustomizer")
    @ConditionalOnMissingBean(name = "momMetricsCommonTagsCustomizer")
    MeterRegistryCustomizer<MeterRegistry> momMetricsCommonTagsCustomizer(Environment environment) {
        String application = environment.getProperty("spring.application.name", "unknown-application");
        String deploymentEnvironment = environment.getProperty(
                "mom.metrics.environment",
                environment.getProperty("MOM_ENVIRONMENT", "local"));
        return registry -> registry.config().commonTags(
                "application", normalize(application, "unknown-application"),
                "environment", normalize(deploymentEnvironment, "local"));
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
