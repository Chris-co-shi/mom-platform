package io.github.chrisshi.mom.bootstrap;

import io.github.chrisshi.mom.core.context.CorrelationHeaders;
import io.github.chrisshi.mom.integration.MomIntegrationApplication;
import io.github.chrisshi.mom.mdm.MomMdmApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration 通过 OpenFeign 调用真实 MDM 应用的回归测试。
 *
 * <p>该测试验证 P01-S02 的同步调用和关联标识传播，不验证分布式事务。由于 Bootstrap 测试类路径同时包含
 * MDM 与 Integration 的全部依赖，必须显式开启技术探针并关闭 Seata，防止 GlobalTransactionScanner 在
 * 没有 TC 的既有 HTTP 回归中启动。Seata Feign XID 传播由独立 P01-S06 双数据库 CI 覆盖。</p>
 */
class IntegrationMdmServiceCallTest {

    private static final String BOOTSTRAP_EXCLUSIONS = String.join(",",
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration");

    @Test
    void integrationCallsRealMdmApplicationAndPropagatesCorrelationId() throws Exception {
        try (ConfigurableApplicationContext mdmContext = startApplication(
                MomMdmApplication.class,
                "spring.application.name=mom-mdm-server")) {
            Integer mdmPort = localPort(mdmContext);
            String mdmUrl = "http://127.0.0.1:" + mdmPort;

            try (ConfigurableApplicationContext integrationContext = startApplication(
                    MomIntegrationApplication.class,
                    "spring.application.name=mom-integration-server",
                    "spring.cloud.openfeign.client.config.mdmServiceProbeClient.url=" + mdmUrl,
                    "spring.cloud.openfeign.client.config.mom-mdm-server.url=" + mdmUrl)) {
                Integer integrationPort = localPort(integrationContext);
                String correlationId = "p01-s02-correlation-001";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + integrationPort
                                + "/integration/mdm-probe"))
                        .header(CorrelationHeaders.CORRELATION_ID, correlationId)
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();

                HttpResponse<String> response = HttpClient.newHttpClient()
                        .send(request, HttpResponse.BodyHandlers.ofString());

                assertEquals(200, response.statusCode());
                assertEquals(correlationId, response.headers()
                        .firstValue(CorrelationHeaders.CORRELATION_ID)
                        .orElseThrow());
                assertTrue(response.body().contains("\"service\":\"mom-integration-server\""));
                assertTrue(response.body().contains("\"mdmService\":\"mom-mdm-server\""));
                assertTrue(response.body().contains("\"correlationId\":\"" + correlationId + "\""));
                assertTrue(response.body().contains("\"mdmCorrelationId\":\"" + correlationId + "\""));
            }
        }
    }

    /**
     * 以指定附加属性启动一个无外部基础设施的真实 Servlet 应用。
     *
     * @param applicationClass 应用入口类型
     * @param additionalProperties 场景特定属性
     * @return 活动应用上下文；调用方负责关闭
     */
    private static ConfigurableApplicationContext startApplication(
            Class<?> applicationClass,
            String... additionalProperties) {
        List<String> properties = new ArrayList<>(List.of(
                "server.port=0",
                "spring.main.banner-mode=off",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "seata.enabled=false",
                "mom.technical-probe.enabled=true",
                "mom.mdm.seata-at-probe.enabled=false",
                "mom.integration.seata-at-probe.enabled=false",
                "spring.autoconfigure.exclude=" + BOOTSTRAP_EXCLUSIONS));
        properties.addAll(Arrays.asList(additionalProperties));

        String[] commandLineArguments = properties.stream()
                .map(property -> "--" + property)
                .toArray(String[]::new);

        return new SpringApplicationBuilder(applicationClass)
                .web(WebApplicationType.SERVLET)
                .run(commandLineArguments);
    }

    /**
     * 读取随机端口启动后的本地服务端口。
     *
     * @param context 已启动的应用上下文
     * @return 分配的本地 HTTP 端口
     */
    private static Integer localPort(ConfigurableApplicationContext context) {
        Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
        assertNotNull(port);
        return port;
    }
}
