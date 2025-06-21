/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.api.gateway.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.wiremock.integrations.testcontainers.WireMockContainer;

class LoggingFilterIntegrationTest extends AbstractIntegrationTest {

    @Container
    static final WireMockContainer wireMockServer =
            new WireMockContainer("wiremock/wiremock:latest-alpine")
                    .withMappingFromResource(
                            "logging-test",
                            LoggingFilterIntegrationTest.class,
                            LoggingFilterIntegrationTest.class.getSimpleName()
                                    + "/logging-test.json");

    static {
        wireMockServer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].uri", wireMockServer::getBaseUrl);
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "logging-test");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].predicates[0]",
                () -> "Path=/test/**");
    }

    @Test
    void shouldLogRequestPath() {
        webTestClient
                .get()
                .uri("/test/endpoint")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .consumeWith(
                        result -> {
                            assertThat(result.getResponseBody()).contains("test response");
                        });
    }

    @Test
    void shouldLogActuatorRequestPathAsTrace() {
        webTestClient
                .get()
                .uri("/actuator/health")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .consumeWith(
                        result -> {
                            assertThat(result.getResponseBody()).contains("UP");
                        });
    }
}
