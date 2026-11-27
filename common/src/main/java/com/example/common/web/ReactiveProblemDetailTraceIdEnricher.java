/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.web;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;

/**
 * Reactive stack variant of {@link ProblemDetailTraceIdEnricher} for Spring WebFlux applications.
 *
 * <p>Operates strictly on error responses by implementing {@link ErrorResponse.Interceptor}. It
 * enriches RFC 7807 {@link ProblemDetail} response bodies with the active trace ID only when an
 * error is being handled, without acting as a global {@code WebFilter} or interfering with gateway
 * request routing.
 */
public class ReactiveProblemDetailTraceIdEnricher implements ErrorResponse.Interceptor {

    public static final String TRACE_ID_HEADER = TraceIdSupport.TRACE_ID_HEADER;

    /**
     * Adds a trace ID to the problem detail when it has no nonblank string trace ID. The first
     * nonblank value from the error response header, active OpenTelemetry span, or MDC keys {@code
     * traceId} and {@code trace_id}, in that order, is used. If none is available, the detail is
     * left unchanged.
     */
    @Override
    public void handleError(@NonNull ProblemDetail detail, @Nullable ErrorResponse errorResponse) {
        TraceIdSupport.enrichProblemDetail(
                detail,
                () ->
                        (errorResponse != null && errorResponse.getHeaders() != null)
                                ? errorResponse.getHeaders().getFirst(TRACE_ID_HEADER)
                                : null);
    }
}
