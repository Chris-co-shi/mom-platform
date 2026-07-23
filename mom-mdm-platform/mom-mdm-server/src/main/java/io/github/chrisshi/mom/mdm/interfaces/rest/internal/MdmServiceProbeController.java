package io.github.chrisshi.mom.mdm.interfaces.rest.internal;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.mdm.api.probe.MdmServiceProbeResponse;
import io.github.chrisshi.mom.tracing.CurrentTraceContext;
import io.github.chrisshi.mom.tracing.TraceContextSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MDM 内部服务发现与追踪技术探针。
 *
 * <p>该 Controller 仅用于 Phase 01 验证 Nacos 注册、OpenFeign 编解码、关联标识和 Trace Context 传播。
 * 路径位于 {@code /internal}，后续接入 Gateway 鉴权后不得作为外部业务接口暴露，也不得在此承载主数据
 * CRUD。</p>
 *
 * <p>Trace 标识通过 MOM 的 Micrometer 访问层读取。可观测性上下文缺失时响应保留空字符串，不因诊断能力
 * 缺失制造业务异常；独立 Observability CI 会要求真实调用场景中标识必须存在。</p>
 */
@RestController
@RequestMapping("/internal/mdm")
@ConditionalOnProperty(prefix = "mom.technical-probe", name = "enabled", havingValue = "true")
public class MdmServiceProbeController {

    private static final Logger LOGGER = LoggerFactory.getLogger(MdmServiceProbeController.class);

    private final CurrentTraceContext currentTraceContext;

    /**
     * 创建 MDM 技术探针。
     *
     * @param currentTraceContext 当前 Micrometer Trace 上下文访问器
     */
    public MdmServiceProbeController(CurrentTraceContext currentTraceContext) {
        this.currentTraceContext = currentTraceContext;
    }

    /**
     * 返回 MDM 技术状态、关联标识和当前 Server Span 标识。
     *
     * @return MDM 技术探针响应
     */
    @GetMapping("/probe")
    MdmServiceProbeResponse probe() {
        String correlationId = CorrelationContext.resolveOrGenerate(CorrelationContext.currentId());
        TraceContextSnapshot trace = currentTraceContext.snapshot();
        LOGGER.info("MDM 技术探针已处理。correlationId={}", correlationId);
        return new MdmServiceProbeResponse(
                "mom-mdm-server",
                "UP",
                correlationId,
                trace.traceId(),
                trace.spanId());
    }
}
