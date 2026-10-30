/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.web;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.util.ClassUtils;
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

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_PROPERTY = "traceId";
    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_TRACE_ID_UNDERSCORE = "trace_id";

    private static final boolean OTEL_PRESENT =
            ClassUtils.isPresent(
                    "io.opentelemetry.api.trace.Span",
                    ReactiveProblemDetailTraceIdEnricher.class.getClassLoader());

    @Override
    public void handleError(@NonNull ProblemDetail detail, @Nullable ErrorResponse errorResponse) {

        String existingTraceId = null;
        if (detail.getProperties() != null) {
            Object val = detail.getProperties().get(TRACE_ID_PROPERTY);
            if (val instanceof String s && !s.isBlank()) {
                existingTraceId = s;
            }
        }

        if (existingTraceId == null) {
            String traceId = resolveTraceId(errorResponse);
            if (traceId != null && !traceId.isBlank()) {
                detail.setProperty(TRACE_ID_PROPERTY, traceId);
            }
        }
    }

    private String resolveTraceId(@Nullable ErrorResponse errorResponse) {
        // 1. Check if error response already carries the X-Trace-Id header
        if (errorResponse != null && errorResponse.getHeaders() != null) {
            String traceId = errorResponse.getHeaders().getFirst(TRACE_ID_HEADER);
            if (traceId != null && !traceId.isBlank()) {
                return traceId;
            }
        }

        // 2. Check active OpenTelemetry span
        if (OTEL_PRESENT) {
            String traceId = OtelTraceIdExtractor.getTraceId();
            if (traceId != null && !traceId.isBlank()) {
                return traceId;
            }
        }

        // 3. Check SLF4J MDC
        String traceId = MDC.get(MDC_TRACE_ID);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }

        traceId = MDC.get(MDC_TRACE_ID_UNDERSCORE);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }

        return null;
    }

    private static class OtelTraceIdExtractor {
        static @Nullable String getTraceId() {
            try {
                io.opentelemetry.api.trace.Span span = io.opentelemetry.api.trace.Span.current();
                if (span != null && span.getSpanContext().isValid()) {
                    return span.getSpanContext().getTraceId();
                }
            } catch (Throwable ignored) {
                // Ignore if OTel runtime is unavailable
            }
            return null;
        }
    }
}
