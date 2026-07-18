package io.github.chrisshi.mom.integration.interfaces.rest;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.integration.api.probe.IntegrationMdmProbeResponse;
import io.github.chrisshi.mom.mdm.api.probe.MdmServiceProbeResponse;
import io.github.chrisshi.mom.mdm.client.MdmServiceProbeClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integration 调用 MDM 的端到端技术探针。
 *
 * <p>Controller 通过 {@link MdmServiceProbeClient} 调用 MDM，只依赖 MDM API 与 Client，不依赖 MDM
 * Server。返回值转换为 Integration 自有响应类型，防止提供方 DTO 穿透成为 Integration 的外部契约。</p>
 *
 * <p>该接口用于 Phase 01 验证服务发现、负载均衡、Feign 编解码和关联标识传播，不承载正式业务逻辑。</p>
 */
@RestController
@RequestMapping("/integration")
public class IntegrationMdmProbeController {

    private final MdmServiceProbeClient mdmServiceProbeClient;

    /**
     * 创建 MDM 调用技术探针。
     *
     * @param mdmServiceProbeClient 只依赖 MDM Client 模块的 Feign 调用契约
     */
    public IntegrationMdmProbeController(MdmServiceProbeClient mdmServiceProbeClient) {
        this.mdmServiceProbeClient = mdmServiceProbeClient;
    }

    /**
     * 通过 OpenFeign 调用 MDM，并返回两端观察到的关联标识。
     *
     * @return Integration 与 MDM 的服务状态和关联标识
     */
    @GetMapping("/mdm-probe")
    IntegrationMdmProbeResponse probeMdm() {
        String correlationId = CorrelationContext.resolveOrGenerate(CorrelationContext.currentId());
        MdmServiceProbeResponse mdm = mdmServiceProbeClient.probe();
        return new IntegrationMdmProbeResponse(
                "mom-integration-server",
                "UP",
                correlationId,
                mdm.service(),
                mdm.status(),
                mdm.correlationId());
    }
}
