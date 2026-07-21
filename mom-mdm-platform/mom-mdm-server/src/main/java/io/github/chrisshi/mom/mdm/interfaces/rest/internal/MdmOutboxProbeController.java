package io.github.chrisshi.mom.mdm.interfaces.rest.internal;

import io.github.chrisshi.mom.core.security.AuditContextExecutor;
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

/** MDM Transactional Outbox 技术接口，写入前显式建立 SYSTEM Actor。 */
@RestController
@RequestMapping("/internal/mdm/outbox-probes")
@ConditionalOnProperty(prefix = "mom.mdm.outbox-probe", name = "enabled", havingValue = "true")
public class MdmOutboxProbeController {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String SYSTEM_ACTOR = "mom-mdm-outbox-probe";
    private final MdmOutboxProbeService service;
    private final AuditContextExecutor auditContextExecutor;

    public MdmOutboxProbeController(MdmOutboxProbeService service, AuditContextExecutor auditContextExecutor) {
        this.service = service;
        this.auditContextExecutor = auditContextExecutor;
    }

    /** 在显式 SYSTEM 上下文中写技术记录和 Outbox。 */
    @PostMapping
    public ResponseEntity<MdmOutboxProbeResponse> create(
            @RequestBody MdmOutboxProbeRequest request,
            @RequestHeader(value = CORRELATION_HEADER, required = false) String correlationId) {
        String effectiveCorrelationId = correlationId == null || correlationId.isBlank()
                ? UUID.randomUUID().toString() : correlationId.trim();
        MdmOutboxProbeResponse response = auditContextExecutor.runAsSystem(SYSTEM_ACTOR,
                () -> MdmOutboxProbeResponse.from(service.create(
                        request.probeKey(), request.probeValue(), effectiveCorrelationId, request.poisonEvent())));
        return ResponseEntity.created(URI.create("/internal/mdm/outbox-probes/" + response.eventId())).body(response);
    }
}
