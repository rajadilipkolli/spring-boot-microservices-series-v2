/***
<p>
    Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.RepeatedTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

class RateLimiterConfigurationIntegrationTest extends AbstractIntegrationTest {

    private static final Logger log =
            LoggerFactory.getLogger(RateLimiterConfigurationIntegrationTest.class);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].uri", wireMockServer::getBaseUrl);
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "order-service");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].predicates[0]",
                () -> "Path=/order-service/**");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].filters[0].name",
                () -> "RequestRateLimiter");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].filters[0].args.redis-rate-limiter.replenishRate",
                () -> "1");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].filters[0].args.redis-rate-limiter.burstCapacity",
                () -> "1");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].filters[0].args.redis-rate-limiter.requestedTokens",
                () -> "1");
    }

    @RepeatedTest(value = 5)
    void orderService() {
        EntityExchangeResult<String> r =
                webTestClient
                        .get()
                        .uri("/order-service/api/{id}", 1)
                        .accept(MediaType.APPLICATION_JSON)
                        .exchange()
                        .expectBody(String.class)
                        .returnResult();

        List<String> remainingToken = r.getResponseHeaders().get("X-RateLimit-Remaining");
        int statusCode = r.getStatus().value();
        log.info(
                "Received: status->{}, payload->{}, remaining->{}",
                statusCode,
                r.getResponseBody(),
                remainingToken);

        assertThat(statusCode).isIn(200, 429);
        assertThat(remainingToken).isNotEmpty().hasSize(1);
    }
}
