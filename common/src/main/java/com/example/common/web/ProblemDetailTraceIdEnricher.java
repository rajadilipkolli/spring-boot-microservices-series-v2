/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.web;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Automatically enriches RFC 7807 {@link ProblemDetail} responses with the current trace ID.
 *
 * <p>Extracts the trace ID with the following precedence:
 *
 * <ol>
 *   <li>The incoming {@code X-Trace-Id} request header (propagated from API Gateway).
 *   <li>The current active OpenTelemetry {@code Span} (if OpenTelemetry API is present).
 *   <li>The SLF4J MDC attribute {@code traceId} or {@code trace_id} (populated by OpenTelemetry
 *       Java Agent).
 * </ol>
 *
 * If a trace ID is resolved and the {@link ProblemDetail} does not already declare a {@code
 * traceId} property, it is added to {@link ProblemDetail#getProperties()}. Additionally, the {@code
 * X-Trace-Id} header is added to the HTTP response if absent.
 */
@ControllerAdvice
public class ProblemDetailTraceIdEnricher implements ResponseBodyAdvice<Object> {

    public static final String TRACE_ID_HEADER = TraceIdSupport.TRACE_ID_HEADER;

    @Override
    public boolean supports(
            @NonNull MethodParameter returnType,
            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public @Nullable Object beforeBodyWrite(
            @Nullable Object body,
            @NonNull MethodParameter returnType,
            @NonNull MediaType selectedContentType,
            @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response) {
        if (body instanceof ProblemDetail problemDetail) {
            enrichProblemDetail(problemDetail, request, response);
        }
        return body;
    }

    private void enrichProblemDetail(
            ProblemDetail problemDetail, ServerHttpRequest request, ServerHttpResponse response) {
        String traceId =
                TraceIdSupport.enrichProblemDetail(
                        problemDetail, () -> request.getHeaders().getFirst(TRACE_ID_HEADER));

        if (traceId != null && response.getHeaders().getFirst(TRACE_ID_HEADER) == null) {
            response.getHeaders().add(TRACE_ID_HEADER, traceId);
        }
    }
}
