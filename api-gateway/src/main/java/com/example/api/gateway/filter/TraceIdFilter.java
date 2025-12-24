/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

// Add Trace ID to HTTP Response Headers (WebFlux)
@Component
@Order(2)
class TraceIdFilter implements WebFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    private final Tracer tracer;

    TraceIdFilter(@Nullable Tracer tracer) {
        this.tracer = tracer;
    }

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
        if (this.tracer == null) {
            return null;
        }
        Span span = this.tracer.currentSpan();
        if (span != null && span.context() != null) {
            return span.context().traceId();
        }
        return Optional.ofNullable(tracer.currentTraceContext().context())
                .map(TraceContext::traceId)
                .orElse(null);
    }
}
