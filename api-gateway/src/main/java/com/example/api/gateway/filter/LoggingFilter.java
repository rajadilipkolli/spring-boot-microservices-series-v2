/***
<p>
    Licensed under MIT License Copyright (c) 2021-2022 Raja Kolli.
</p>
***/

package com.example.api.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

@Component
class LoggingFilter implements GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // skip actuator traces
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
                                if (exchange.getResponse() != null
                                        && exchange.getResponse().getStatusCode() != null) {
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
}
