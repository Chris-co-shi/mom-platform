package io.github.chrisshi.mom.mdm.interfaces.rest.internal;

import io.github.chrisshi.mom.mdm.application.MdmOutboxProbeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * MDM Transactional Outbox 内部技术验证接口。
 *
 * <p>该接口只用于 P01-S05 验证领域写入、Outbox、Spring Cloud Stream 和 RocketMQ，不是正式 MDM API。
 * 只有显式设置 {@code mom.mdm.outbox-probe.enabled=true} 时才注册。正常请求只提交数据库本地事务，不等待
 * Broker 或消费者，因此即使 RocketMQ 暂时不可用也可以返回 HTTP 201，事件由 Outbox 后续恢复发布。</p>
 */
@RestController
@RequestMapping("/internal/mdm/outbox-probes")
@ConditionalOnProperty(
        prefix = "mom.mdm.outbox-probe",
        name = "enabled",
        havingValue = "true")
public class MdmOutboxProbeController {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final MdmOutboxProbeService service;

    /**
     * 创建 MDM Outbox 技术验证 Controller。
     *
     * @param service 业务记录与 Outbox 原子写入服务
     */
    public MdmOutboxProbeController(MdmOutboxProbeService service) {
        this.service = service;
    }

    /**
     * 在一个本地事务中写入技术业务记录和 Outbox 事件。
     *
     * @param request 技术记录和故障事件开关
     * @param correlationId 可选调用链关联标识；缺失时生成新的技术验证标识
     * @return HTTP 201 与业务记录 ID、事件 ID
     */
    @PostMapping
    public ResponseEntity<MdmOutboxProbeResponse> create(
            @RequestBody MdmOutboxProbeRequest request,
            @RequestHeader(value = CORRELATION_HEADER, required = false) String correlationId) {
        String effectiveCorrelationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString()
                : correlationId.trim();
        MdmOutboxProbeResponse response = MdmOutboxProbeResponse.from(service.create(
                request.probeKey(),
                request.probeValue(),
                effectiveCorrelationId,
                request.poisonEvent()));
        return ResponseEntity.created(URI.create(
                        "/internal/mdm/outbox-probes/" + response.eventId()))
                .body(response);
    }
}
