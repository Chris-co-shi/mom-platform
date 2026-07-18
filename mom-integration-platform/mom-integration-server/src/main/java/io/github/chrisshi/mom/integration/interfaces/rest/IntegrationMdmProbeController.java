package io.github.chrisshi.mom.integration.interfaces.rest;

import io.github.chrisshi.mom.core.context.CorrelationContext;
import io.github.chrisshi.mom.integration.api.probe.IntegrationMdmProbeResponse;
import io.github.chrisshi.mom.mdm.api.probe.MdmServiceProbeResponse;
import io.github.chrisshi.mom.mdm.client.MdmServiceProbeClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/integration")
public class IntegrationMdmProbeController {

    private final MdmServiceProbeClient mdmServiceProbeClient;

    public IntegrationMdmProbeController(MdmServiceProbeClient mdmServiceProbeClient) {
        this.mdmServiceProbeClient = mdmServiceProbeClient;
    }

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
