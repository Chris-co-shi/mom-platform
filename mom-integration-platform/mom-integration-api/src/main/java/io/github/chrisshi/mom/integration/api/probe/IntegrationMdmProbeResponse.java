package io.github.chrisshi.mom.integration.api.probe;

/**
 * End-to-end response proving that Integration reached MDM and preserved correlation data.
 */
public record IntegrationMdmProbeResponse(
        String service,
        String status,
        String correlationId,
        String mdmService,
        String mdmStatus,
        String mdmCorrelationId) {
}
