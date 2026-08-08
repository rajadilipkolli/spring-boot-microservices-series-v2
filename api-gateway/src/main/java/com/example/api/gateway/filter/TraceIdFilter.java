/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

// Add Trace ID to HTTP Response Headers (WebFlux)
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class TraceIdFilter implements WebFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    TraceIdFilter() {}

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse()
                .beforeCommit(
                        () -> {
                            String traceId = getTraceId();
                            if (traceId != null) {
                                exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, traceId);
                            }
                            return Mono.empty();
                        });
        return chain.filter(exchange);
    }

    private String getTraceId() {
        SpanContext spanContext = Span.current().getSpanContext();
        String traceId = null;
        if (spanContext.isValid()) {
            traceId = spanContext.getTraceId();
        }
        // Generate a random UUID if traceId is still null as fallback
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        return traceId;
    }
}
