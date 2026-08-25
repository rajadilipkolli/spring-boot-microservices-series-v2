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

    @Override
    public @NonNull Mono<Void> filter(ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        // Safely add headers to the response before it commits
        exchange.getResponse()
                .beforeCommit(
                        () -> {
                            String traceId = getTraceId();
                            if (traceId != null) {
                                exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, traceId);
                            }
                            return Mono.empty();
                        });

        // 2. Logging & Timing Logic
        if (exchange.getRequest().getURI().getPath().contains("/actuator")) {
            if (log.isTraceEnabled()) {
                log.trace("Path of the request received -> {}", exchange.getRequest().getPath());
            }
            return chain.filter(exchange);
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        log.info(
                "Path of the request received -> {} with method {}",
                exchange.getRequest().getPath(),
                exchange.getRequest().getMethod());

        return chain.filter(exchange)
                .doFinally(
                        (SignalType signal) -> {
                            Integer status = null;
                            try {
                                exchange.getResponse();
                                if (exchange.getResponse().getStatusCode() != null) {
                                    status = exchange.getResponse().getStatusCode().value();
                                }
                            } catch (Exception e) {
                                // ignore - best effort to read status
                            }
                            stopWatch.stop();
                            long took = stopWatch.getTotalTimeMillis();
                            log.info(
                                    "Request {} {} -> status={} took={}ms",
                                    exchange.getRequest().getMethod(),
                                    exchange.getRequest().getURI().getPath(),
                                    status == null ? "UNKNOWN" : status,
                                    took);
                        });
    }

    private String getTraceId() {
        Span span = Span.current();
        if (span.getSpanContext().isValid()) {
            return span.getSpanContext().getTraceId();
        }
        return UUID.randomUUID().toString();
    }
}
