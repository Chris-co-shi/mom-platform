package io.github.chrisshi.mom.core.context;

/**
 * Standard HTTP headers used to correlate a request across MOM services.
 */
public final class CorrelationHeaders {

    public static final String CORRELATION_ID = "X-Correlation-Id";

    private CorrelationHeaders() {
    }
}
