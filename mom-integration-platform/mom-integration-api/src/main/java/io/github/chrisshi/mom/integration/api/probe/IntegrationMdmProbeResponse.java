package io.github.chrisshi.mom.integration.api.probe;

import io.github.chrisshi.mom.mdm.api.probe.MdmServiceProbeResponse;

/**
 * End-to-end response proving that Integration reached MDM and preserved correlation data.
 */
public record IntegrationMdmProbeResponse(
        String service,
        String status,
        String correlationId,
        MdmServiceProbeResponse mdm) {
}
