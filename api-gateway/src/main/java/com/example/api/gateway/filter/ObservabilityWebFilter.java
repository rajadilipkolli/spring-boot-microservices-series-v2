/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.filter;

import io.opentelemetry.api.trace.Span;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Combined WebFilter that handles: 1. Propagating OpenTelemetry Trace ID in request and response
 * (Skipped for Actuator) 2. Request Logging, Execution Timing, and MDC context management (Skipped
 * for Actuator)
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ObservabilityWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityWebFilter.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_ID = "traceId";

    private static final Pattern ACTUATOR_PATH_PATTERN =
            Pattern.compile("^(/[^/]+)?/actuator(/.*)?$");

    /**
     * Passes the request through the chain with a trace ID from the current span, or a random UUID
     * without hyphens when no valid span exists. Adds that ID as {@code X-Trace-Id} to the
     * downstream request when absent, and as {@code X-Trace-Id} before the response commits.
     *
     * @param exchange current request and response
     * @param chain remaining request filters
     * @return completion of the remaining chain, propagating its errors
     */
    @Override
    public @NonNull Mono<Void> filter(
            @NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {

        String incomingTraceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        String traceId = (StringUtils.hasText(incomingTraceId)) ? incomingTraceId : getTraceId();

        // 1. Add trace ID to the downstream request headers if absent
        ServerWebExchange effectiveExchange = addTraceIdRequestHeader(exchange, traceId);

        // 2. Add trace ID to the response before committing
        addTraceIdResponseHeader(effectiveExchange, traceId);

        // 3. Bypass logging & timing for Actuator endpoints
        if (isActuatorRequest(effectiveExchange)) {
            if (log.isTraceEnabled()) {
                log.trace(
                        "Path of the request received -> {}",
                        effectiveExchange.getRequest().getPath());
            }
            return chain.filter(effectiveExchange);
        }

        // 4. Execute request with MDC context propagation and timing
        return chain.filter(effectiveExchange)
                .transform(deferred -> logAndMeasureRequest(effectiveExchange, deferred, traceId))
                .contextWrite(context -> context.put(MDC_TRACE_ID, traceId));
    }

    private ServerWebExchange addTraceIdRequestHeader(ServerWebExchange exchange, String traceId) {
        String existing = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        if (StringUtils.hasText(existing)) {
            return exchange;
        }

        return exchange.mutate()
                .request(
                        request ->
                                request.headers(headers -> headers.set(TRACE_ID_HEADER, traceId)))
                .build();
    }

    private void addTraceIdResponseHeader(ServerWebExchange exchange, String traceId) {
        exchange.getResponse()
                .beforeCommit(
                        () -> {
                            if (StringUtils.hasText(traceId)) {
                                exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
                            }
                            return Mono.empty();
                        });
    }

    private boolean isActuatorRequest(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        return path != null && ACTUATOR_PATH_PATTERN.matcher(path).matches();
    }

    private Mono<Void> logAndMeasureRequest(
            ServerWebExchange exchange, Mono<Void> chainMono, String traceId) {

        long startTime = System.nanoTime();

        return Mono.deferContextual(
                contextView -> {
                    String ctxTraceId = contextView.getOrDefault(MDC_TRACE_ID, traceId);

                    runWithMdc(
                            ctxTraceId,
                            () ->
                                    log.info(
                                            "Path of the request received -> {} with method {}",
                                            exchange.getRequest().getPath(),
                                            exchange.getRequest().getMethod()));

                    return chainMono.doFinally(
                            _ -> {
                                Integer status =
                                        exchange.getResponse().getStatusCode() != null
                                                ? exchange.getResponse().getStatusCode().value()
                                                : null;

                                long took =
                                        TimeUnit.NANOSECONDS.toMillis(
                                                System.nanoTime() - startTime);

                                runWithMdc(
                                        ctxTraceId,
                                        () ->
                                                log.info(
                                                        "Request {} {} -> status={} took={}ms",
                                                        exchange.getRequest().getMethod(),
                                                        exchange.getRequest().getURI().getPath(),
                                                        status == null ? "UNKNOWN" : status,
                                                        took));
                            });
                });
    }

    private void runWithMdc(String traceId, Runnable action) {
        String prevTraceId = MDC.get(MDC_TRACE_ID);
        try {
            if (StringUtils.hasText(traceId)) {
                MDC.put(MDC_TRACE_ID, traceId);
            } else {
                MDC.remove(MDC_TRACE_ID);
            }
            action.run();
        } finally {
            if (prevTraceId != null) {
                MDC.put(MDC_TRACE_ID, prevTraceId);
            } else {
                MDC.remove(MDC_TRACE_ID);
            }
        }
    }

    /**
     * Returns the current span's trace ID, or a new random UUID without hyphens when the span
     * context is invalid.
     */
    private String getTraceId() {
        Span span = Span.current();
        if (span != null && span.getSpanContext().isValid()) {
            return span.getSpanContext().getTraceId();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
