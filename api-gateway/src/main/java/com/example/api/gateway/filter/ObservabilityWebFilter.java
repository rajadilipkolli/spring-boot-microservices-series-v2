/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.filter;

import io.opentelemetry.api.trace.Span;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Combined WebFilter that handles: 1. Propagating Trace/Correlation IDs in request and response
 * (Skipped for Actuator) 2. Request Logging, Execution Timing, and MDC context management (Skipped
 * for Actuator)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ObservabilityWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityWebFilter.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_CORRELATION_ID = "correlationId";

    /**
     * Passes the request through the chain with a trace ID from the current span, or a random UUID
     * without hyphens when no valid span exists. Adds that ID as {@code X-Correlation-Id} only when
     * the request header is absent, and as {@code X-Trace-Id} before the response commits.
     *
     * @param exchange current request and response
     * @param chain remaining request filters
     * @return completion of the remaining chain, propagating its errors
     */
    @Override
    public @NonNull Mono<Void> filter(
            @NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {

        // Completely ignore and bypass checking/injecting for actuator endpoints
        if (isActuatorRequest(exchange)) {
            return chain.filter(exchange);
        }

        String traceId = getTraceId();

        // 1. Determine or retrieve correlation ID
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = traceId;
        }

        // 2. Add correlation ID to the request headers if absent
        ServerWebExchange effectiveExchange = addCorrelationId(exchange, correlationId);

        // 3. Add trace ID to the response before committing
        addTraceIdResponseHeader(effectiveExchange, traceId);

        // 4. Execute request with MDC context propagation and timing
        final String finalCorrelationId = correlationId;
        return chain.filter(effectiveExchange)
                .transform(
                        deferred ->
                                logAndMeasureRequest(
                                        effectiveExchange, deferred, traceId, finalCorrelationId));
    }

    private ServerWebExchange addCorrelationId(ServerWebExchange exchange, String correlationId) {
        if (exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER) != null) {
            return exchange;
        }

        return exchange.mutate()
                .request(request -> request.header(CORRELATION_ID_HEADER, correlationId))
                .build();
    }

    private void addTraceIdResponseHeader(ServerWebExchange exchange, String traceId) {
        exchange.getResponse()
                .beforeCommit(
                        () -> {
                            if (traceId != null) {
                                exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
                            }
                            return Mono.empty();
                        });
    }

    private boolean isActuatorRequest(ServerWebExchange exchange) {
        return exchange.getRequest().getURI().getPath().contains("/actuator");
    }

    private Mono<Void> logAndMeasureRequest(
            ServerWebExchange exchange,
            Mono<Void> chainMono,
            String traceId,
            String correlationId) {

        long startTime = System.nanoTime();

        log.info(
                "Path of the request received -> {} with method {}",
                exchange.getRequest().getPath(),
                exchange.getRequest().getMethod());

        return Mono.defer(
                () -> {
                    // Populate MDC for the reactive pipeline
                    MDC.put(MDC_TRACE_ID, traceId);
                    MDC.put(MDC_CORRELATION_ID, correlationId);

                    return chainMono
                            .doFinally(
                                    _ -> {
                                        Integer status =
                                                exchange.getResponse().getStatusCode() != null
                                                        ? exchange.getResponse()
                                                                .getStatusCode()
                                                                .value()
                                                        : null;

                                        long took =
                                                TimeUnit.NANOSECONDS.toMillis(
                                                        System.nanoTime() - startTime);

                                        log.info(
                                                "Request {} {} -> status={} took={}ms",
                                                exchange.getRequest().getMethod(),
                                                exchange.getRequest().getURI().getPath(),
                                                status == null ? "UNKNOWN" : status,
                                                took);
                                    })
                            .doFinally(_ -> MDC.clear()); // Unnamed variable '_' fully
                    // supported natively in Java 25
                });
    }

    private String getTraceId() {
        Span span = Span.current();
        if (span != null && span.getSpanContext().isValid()) {
            return span.getSpanContext().getTraceId();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
