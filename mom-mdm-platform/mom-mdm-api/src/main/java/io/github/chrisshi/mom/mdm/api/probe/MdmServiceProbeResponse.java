package io.github.chrisshi.mom.mdm.api.probe;

/**
 * Minimal MDM provider response used to validate service discovery and contracts.
 */
public record MdmServiceProbeResponse(
        String service,
        String status,
        String correlationId) {
}
