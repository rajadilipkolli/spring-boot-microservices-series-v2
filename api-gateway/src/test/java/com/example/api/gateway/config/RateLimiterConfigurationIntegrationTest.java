/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
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
import org.testcontainers.junit.jupiter.Container;
import org.wiremock.integrations.testcontainers.WireMockContainer;

class RateLimiterConfigurationIntegrationTest extends AbstractIntegrationTest {

    private static final Logger log =
            LoggerFactory.getLogger(RateLimiterConfigurationIntegrationTest.class);

    @Container
    static final WireMockContainer wireMockServer =
            new WireMockContainer("wiremock/wiremock:latest-alpine")
                    .withMappingFromResource(
                            "order-by-id",
                            RateLimiterConfigurationIntegrationTest.class,
                            RateLimiterConfigurationIntegrationTest.class.getSimpleName()
                                    + "/mocks-config.json");

    static {
        wireMockServer.start();
    }

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
                () -> "10");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].filters[0].args.redis-rate-limiter.burstCapacity",
                () -> "20");
    }

    @RepeatedTest(value = 60)
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
