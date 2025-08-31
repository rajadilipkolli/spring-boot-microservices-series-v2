/***
<p>
    Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli.
</p>
***/

package com.example.api.gateway.filter;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Order(1)
public class CorrelationIdGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    private static final Logger log =
            LoggerFactory.getLogger(CorrelationIdGatewayFilterFactory.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> {
            String correlationId =
                    exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);

            final String finalCorrelationId;
            if (correlationId == null || correlationId.isEmpty()) {
                finalCorrelationId = UUID.randomUUID().toString();
                log.debug("Generated new correlation ID: {}", finalCorrelationId);
            } else {
                finalCorrelationId = correlationId;
                log.debug("Using existing correlation ID: {}", finalCorrelationId);
            }

            // Add correlation ID to request
            var request =
                    exchange.getRequest()
                            .mutate()
                            .header(CORRELATION_ID_HEADER, finalCorrelationId)
                            .build();

            // Add correlation ID to response
            exchange.getResponse()
                    .beforeCommit(
                            () -> {
                                exchange.getResponse()
                                        .getHeaders()
                                        .add(CORRELATION_ID_HEADER, finalCorrelationId);
                                return Mono.empty();
                            });

            return chain.filter(exchange.mutate().request(request).build());
        };
    }
}
