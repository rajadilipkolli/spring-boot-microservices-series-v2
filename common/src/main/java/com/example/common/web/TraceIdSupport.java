/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.web;

import java.util.function.Supplier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;
import org.springframework.util.ClassUtils;

/**
 * Shared utility encapsulating trace ID resolution and {@link ProblemDetail} enrichment following
 * Single Responsibility and DRY principles.
 *
 * <p>Resolves the trace ID in the following order:
 *
 * <ol>
 *   <li>Supplied header value (e.g. from {@code X-Trace-Id}).
 *   <li>Current active OpenTelemetry {@code Span} (if OpenTelemetry API is present).
 *   <li>SLF4J MDC attributes {@code traceId} or {@code trace_id} (e.g. from OTel agent).
 * </ol>
 */
public final class TraceIdSupport {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_PROPERTY = "traceId";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_TRACE_ID_UNDERSCORE = "trace_id";

    private static final boolean OTEL_PRESENT =
            ClassUtils.isPresent(
                    "io.opentelemetry.api.trace.Span", TraceIdSupport.class.getClassLoader());

    private TraceIdSupport() {
        // Utility class
    }

    /**
     * Resolves the trace ID from the given header supplier (if any), falling back to OpenTelemetry
     * span context or SLF4J MDC.
     *
     * @param headerSupplier optional supplier returning the incoming header value
     * @return resolved trace ID, or {@code null} if none is found
     */
    public static @Nullable String resolveTraceId(@Nullable Supplier<String> headerSupplier) {
        if (headerSupplier != null) {
            String headerTraceId = headerSupplier.get();
            if (headerTraceId != null && !headerTraceId.isBlank()) {
                return headerTraceId;
            }
        }

        if (OTEL_PRESENT) {
            String otelTraceId = OtelTraceIdExtractor.getTraceId();
            if (otelTraceId != null && !otelTraceId.isBlank()) {
                return otelTraceId;
            }
        }

        String mdcTraceId = MDC.get(MDC_TRACE_ID);
        if (mdcTraceId != null && !mdcTraceId.isBlank()) {
            return mdcTraceId;
        }

        String mdcTraceIdUnderscore = MDC.get(MDC_TRACE_ID_UNDERSCORE);
        if (mdcTraceIdUnderscore != null && !mdcTraceIdUnderscore.isBlank()) {
            return mdcTraceIdUnderscore;
        }

        return null;
    }

    /**
     * Extracts an existing non-blank {@code traceId} string property from the given {@link
     * ProblemDetail}.
     *
     * @param problemDetail the problem detail to inspect
     * @return the existing trace ID, or {@code null} if absent or blank
     */
    public static @Nullable String getExistingTraceId(@NonNull ProblemDetail problemDetail) {
        if (problemDetail.getProperties() != null) {
            Object val = problemDetail.getProperties().get(TRACE_ID_PROPERTY);
            if (val instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    /**
     * Enriches {@link ProblemDetail} with a resolved trace ID if none is already present.
     *
     * @param problemDetail the problem detail to enrich
     * @param headerSupplier optional supplier returning incoming header value
     * @return the effective trace ID (existing or newly resolved), or {@code null} if none found
     */
    public static @Nullable String enrichProblemDetail(
            @NonNull ProblemDetail problemDetail, @Nullable Supplier<String> headerSupplier) {
        String existingTraceId = getExistingTraceId(problemDetail);
        String traceId = existingTraceId != null ? existingTraceId : resolveTraceId(headerSupplier);

        if (traceId != null && !traceId.isBlank()) {
            if (existingTraceId == null) {
                problemDetail.setProperty(TRACE_ID_PROPERTY, traceId);
            }
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
