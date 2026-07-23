package io.github.chrisshi.mom.bootstrap;

import io.github.chrisshi.mom.iam.MomIamApplication;
import io.github.chrisshi.mom.integration.MomIntegrationApplication;
import io.github.chrisshi.mom.mdm.MomMdmApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台服务无外部基础设施启动回归测试。
 *
 * <p>测试模块会把多个可执行服务同时放入一个 JVM 测试类路径，因此某个服务新增的 Starter 也可能被其他
 * 应用上下文看到。这里显式关闭 Nacos、数据库和 Seata，验证所有这些能力在默认关闭场景不会被类路径存在性
 * 意外激活。Seata 的真实启用行为由独立双数据库 CI 验证，不能用本测试替代。</p>
 */
class PlatformApplicationBootstrapTest {

    @Test
    void iamStartsWithoutExternalInfrastructure() {
        assertApplicationStarts(MomIamApplication.class, true);
    }

    @Test
    void mdmStartsWithoutExternalInfrastructure() {
        assertApplicationStarts(MomMdmApplication.class, false);
    }

    @Test
    void integrationStartsWithoutExternalInfrastructure() {
        assertApplicationStarts(MomIntegrationApplication.class, false);
    }

    /**
     * 使用统一离线参数启动指定应用并验证健康端点存在。
     *
     * @param applicationClass 待启动的 Spring Boot 应用入口
     * @param explicitlyEnableIamAdmin 是否显式开启 IAM Admin；IAM 无数据库场景用于验证 JdbcTemplate 条件
     */
    private void assertApplicationStarts(
            Class<?> applicationClass,
            boolean explicitlyEnableIamAdmin) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(applicationClass)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "spring.main.banner-mode=off",
                        "spring.cloud.nacos.discovery.enabled=false",
                        "spring.cloud.nacos.config.enabled=false",
                        "seata.enabled=false",
                        "mom.mdm.seata-at-probe.enabled=false",
                        "mom.integration.seata-at-probe.enabled=false",
                        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                        "management.endpoints.enabled-by-default=true");
        if (explicitlyEnableIamAdmin) {
            builder.properties("mom.iam.admin.enabled=true");
        }
        try (ConfigurableApplicationContext context = builder.run()) {
            assertTrue(context.isActive());
            assertNotNull(context.getBean(HealthEndpoint.class));
            assertFalse(context.containsBean("iamAdminJdbcRepository"));
            assertFalse(context.containsBean("iamAdminService"));
            assertFalse(context.containsBean("iamAdminController"));
            assertFalse(context.containsBean("integrationMdmProbeController"));
            assertFalse(context.containsBean("integrationIdempotencyProbeController"));
            assertFalse(context.containsBean("mdmServiceProbeController"));
        }
    }
}
