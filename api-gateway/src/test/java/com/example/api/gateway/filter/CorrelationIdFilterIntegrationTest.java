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

public class CorrelationIdFilterIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("app.cors.pathPattern", () -> "/api/**");
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "correlation-test");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].uri", wireMockServer::getBaseUrl);
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].predicates[0]",
                () -> "Path=/api/correlation/**");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].filters[0]",
                () -> "RewritePath=/api/correlation/(?<segment>.*), /${segment}");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].filters[1]", () -> "CorrelationId");
    }

    @Test
    void shouldGenerateCorrelationIdWhenNotProvided() {
        webTestClient
                .get()
                .uri("/api/correlation/test")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .exists("X-Correlation-ID")
                .expectHeader()
                .value(
                        "X-Correlation-ID",
                        correlationId -> {
                            assertThat(correlationId).isNotNull();
                            assertThat(correlationId).isNotEmpty();
                            // Verify it's a valid UUID format
                            assertThat(correlationId)
                                    .matches(
                                            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
                        });
    }

    @Test
    void shouldUseProvidedCorrelationId() {
        String providedCorrelationId = "test-correlation-id-12345";

        webTestClient
                .get()
                .uri("/api/correlation/test")
                .header("X-Correlation-ID", providedCorrelationId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-Correlation-ID", providedCorrelationId);
    }

    @Test
    void shouldGenerateNewCorrelationIdWhenProvidedIsEmpty() {
        webTestClient
                .get()
                .uri("/api/correlation/test")
                .header("X-Correlation-ID", "")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .exists("X-Correlation-ID")
                .expectHeader()
                .value(
                        "X-Correlation-ID",
                        correlationId -> {
                            assertThat(correlationId).isNotNull();
                            assertThat(correlationId).isNotEmpty();
                            // Verify it's a valid UUID format
                            assertThat(correlationId)
                                    .matches(
                                            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
                        });
    }

    @Test
    void shouldGenerateNewCorrelationIdWhenProvidedIsNull() {
        webTestClient
                .get()
                .uri("/api/correlation/test")
                .header("X-Correlation-ID", (String) null)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .exists("X-Correlation-ID")
                .expectHeader()
                .value(
                        "X-Correlation-ID",
                        correlationId -> {
                            assertThat(correlationId).isNotNull();
                            assertThat(correlationId).isNotEmpty();
                            // Verify it's a valid UUID format
                            assertThat(correlationId)
                                    .matches(
                                            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
                        });
    }

    @Test
    void shouldPreserveCorrelationIdThroughMultipleRequests() {
        String firstCorrelationId = "first-request-correlation-id";
        String secondCorrelationId = "second-request-correlation-id";

        // First request with provided correlation ID
        webTestClient
                .get()
                .uri("/api/correlation/test")
                .header("X-Correlation-ID", firstCorrelationId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-Correlation-ID", firstCorrelationId);

        // Second request with different correlation ID
        webTestClient
                .get()
                .uri("/api/correlation/test")
                .header("X-Correlation-ID", secondCorrelationId)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-Correlation-ID", secondCorrelationId);
    }
}
