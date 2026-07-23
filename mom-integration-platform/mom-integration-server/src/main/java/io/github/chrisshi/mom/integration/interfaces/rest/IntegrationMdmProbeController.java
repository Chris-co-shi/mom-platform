package io.github.chrisshi.mom.integration.interfaces.rest;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.integration.api.probe.IntegrationMdmProbeResponse;
import io.github.chrisshi.mom.mdm.api.probe.MdmServiceProbeResponse;
import io.github.chrisshi.mom.mdm.client.MdmServiceProbeClient;
import io.github.chrisshi.mom.tracing.CurrentTraceContext;
import io.github.chrisshi.mom.tracing.TraceContextSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integration 调用 MDM 的端到端追踪技术探针。
 *
 * <p>Controller 通过 {@link MdmServiceProbeClient} 调用 MDM，只依赖 MDM API 与 Client，不依赖 MDM Server。
 * 返回值转换为 Integration 自有响应类型，防止提供方 DTO 穿透成为 Integration 的外部契约。</p>
 *
 * <p>该接口用于 Phase 01 验证服务发现、负载均衡、Feign 编解码、关联标识以及 W3C Trace Context 传播。
 * Integration 与 MDM 应观察到相同 Trace ID、不同 Server Span ID；具体 OpenTelemetry SDK 类型不会进入业务
 * 契约。</p>
 */
@RestController
@RequestMapping("/integration")
@ConditionalOnProperty(prefix = "mom.technical-probe", name = "enabled", havingValue = "true")
public class IntegrationMdmProbeController {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationMdmProbeController.class);

    private final MdmServiceProbeClient mdmServiceProbeClient;
    private final CurrentTraceContext currentTraceContext;

    /**
     * 创建 MDM 调用技术探针。
     *
     * @param mdmServiceProbeClient 只依赖 MDM Client 模块的 Feign 调用契约
     * @param currentTraceContext 当前 Micrometer Trace 上下文访问器
     */
    public IntegrationMdmProbeController(
            MdmServiceProbeClient mdmServiceProbeClient,
            CurrentTraceContext currentTraceContext) {
        this.mdmServiceProbeClient = mdmServiceProbeClient;
        this.currentTraceContext = currentTraceContext;
    }

    /**
     * 通过 OpenFeign 调用 MDM，并返回两端观察到的关联标识与 Trace 标识。
     *
     * @return Integration 与 MDM 的服务状态、关联标识和当前 Span 标识
     */
    @GetMapping("/mdm-probe")
    IntegrationMdmProbeResponse probeMdm() {
        String correlationId = CorrelationContext.resolveOrGenerate(CorrelationContext.currentId());
        TraceContextSnapshot integrationTrace = currentTraceContext.snapshot();
        MdmServiceProbeResponse mdm = mdmServiceProbeClient.probe();
        LOGGER.info("Integration 技术探针已完成 MDM 调用。correlationId={}", correlationId);
        return new IntegrationMdmProbeResponse(
                "mom-integration-server",
                "UP",
                correlationId,
                integrationTrace.traceId(),
                integrationTrace.spanId(),
                mdm.service(),
                mdm.status(),
                mdm.correlationId(),
                mdm.traceId(),
                mdm.spanId());
    }
}
