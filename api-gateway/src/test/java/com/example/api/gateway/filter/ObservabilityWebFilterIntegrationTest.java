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

public class ObservabilityWebFilterIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Trace ID route config
        registry.add("app.cors.pathPattern", () -> "/api/**");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "trace-test");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].uri", wireMockServer::getBaseUrl);
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].predicates[0]",
                () -> "Path=/api/trace/**");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].filters[0]",
                () -> "RewritePath=/api/trace/(?<segment>.*), /${segment}");

        // Logging route config
        registry.add("spring.cloud.gateway.server.webflux.routes[1].id", () -> "logging-test");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[1].uri", wireMockServer::getBaseUrl);
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[1].predicates[0]",
                () -> "Path=/test/**");
    }

    @Test
    void shouldInjectTraceIdIntoResponse() {
        webTestClient
                .get()
                .uri("/api/trace/test")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .exists("X-Trace-Id")
                .expectHeader()
                .value(
                        "X-Trace-Id",
                        traceId -> {
                            assertThat(traceId).isNotNull();
                            assertThat(traceId).isNotEmpty();
                        });
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
                .expectHeader()
                .exists("X-Trace-Id")
                .expectBody(String.class)
                .consumeWith(
                        result -> assertThat(result.getResponseBody()).contains("test response"));
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
                .expectHeader()
                .exists("X-Trace-Id")
                .expectBody(String.class)
                .consumeWith(result -> assertThat(result.getResponseBody()).contains("UP"));
    }
}
