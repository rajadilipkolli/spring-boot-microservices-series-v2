/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.filter;

import io.opentelemetry.api.trace.Span;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * Combined WebFilter that handles: 1. Propagating Trace IDs in the response 3. Request Logging &
 * Execution Timing
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ObservabilityWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityWebFilter.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

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
        String traceId = getTraceId();
        ServerWebExchange effectiveExchange = exchange;
        if (traceId != null
                && exchange.getRequest().getHeaders().getFirst("X-Correlation-Id") == null) {
            effectiveExchange =
                    exchange.mutate().request(r -> r.header("X-Correlation-Id", traceId)).build();
        }

        final ServerWebExchange finalExchange = effectiveExchange;

        // Safely add headers to the response before it commits
        finalExchange
                .getResponse()
                .beforeCommit(
                        () -> {
                            if (traceId != null) {
                                finalExchange
                                        .getResponse()
                                        .getHeaders()
                                        .add(TRACE_ID_HEADER, traceId);
                            }
                            return Mono.empty();
                        });

        // 2. Logging & Timing Logic
        if (finalExchange.getRequest().getURI().getPath().contains("/actuator")) {
            if (log.isTraceEnabled()) {
                log.trace(
                        "Path of the request received -> {}", finalExchange.getRequest().getPath());
            }
            return chain.filter(finalExchange);
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        log.info(
                "Path of the request received -> {} with method {}",
                finalExchange.getRequest().getPath(),
                finalExchange.getRequest().getMethod());

        return chain.filter(finalExchange)
                .doFinally(
                        (SignalType _) -> {
                            Integer status = null;
                            try {
                                finalExchange.getResponse();
                                if (finalExchange.getResponse().getStatusCode() != null) {
                                    status = finalExchange.getResponse().getStatusCode().value();
                                }
                            } catch (Exception e) {
                                // ignore - best effort to read status
                            }
                            stopWatch.stop();
                            long took = stopWatch.getTotalTimeMillis();
                            log.info(
                                    "Request {} {} -> status={} took={}ms",
                                    finalExchange.getRequest().getMethod(),
                                    finalExchange.getRequest().getURI().getPath(),
                                    status == null ? "UNKNOWN" : status,
                                    took);
                        });
    }

    /**
     * Returns the current span's trace ID, or a new random UUID without hyphens when the span
     * context is invalid.
     */
    private String getTraceId() {
        Span span = Span.current();
        if (span.getSpanContext().isValid()) {
            return span.getSpanContext().getTraceId();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
