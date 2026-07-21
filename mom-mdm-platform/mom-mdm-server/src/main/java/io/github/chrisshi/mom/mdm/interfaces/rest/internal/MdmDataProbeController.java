package io.github.chrisshi.mom.mdm.interfaces.rest.internal;

import io.github.chrisshi.mom.core.security.AuditContextExecutor;
import io.github.chrisshi.mom.mdm.application.MdmDataProbeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** MDM PostgreSQL 技术探针；Slice 03 前的写操作显式使用稳定 SYSTEM Actor。 */
@RestController
@RequestMapping("/internal/mdm/data-probes")
@ConditionalOnProperty(prefix = "mom.mdm.data-probe", name = "enabled", havingValue = "true")
public class MdmDataProbeController {

    private static final String SYSTEM_ACTOR = "mom-mdm-data-probe";
    private final MdmDataProbeService service;
    private final AuditContextExecutor auditContextExecutor;

    public MdmDataProbeController(MdmDataProbeService service, AuditContextExecutor auditContextExecutor) {
        this.service = service;
        this.auditContextExecutor = auditContextExecutor;
    }

    /** 写入技术记录并返回 HTTP 201。 */
    @PostMapping
    public ResponseEntity<MdmDataProbeResponse> create(@RequestBody MdmDataProbeRequest request) {
        MdmDataProbeResponse response = auditContextExecutor.runAsSystem(SYSTEM_ACTOR,
                () -> MdmDataProbeResponse.from(service.create(request.probeKey(), request.probeValue())));
        return ResponseEntity.created(URI.create("/internal/mdm/data-probes/" + response.probeKey())).body(response);
    }

    /** 根据验证键读取记录。 */
    @GetMapping("/{probeKey}")
    public ResponseEntity<MdmDataProbeResponse> findByKey(@PathVariable String probeKey) {
        return service.findByKey(probeKey).map(MdmDataProbeResponse::from)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
