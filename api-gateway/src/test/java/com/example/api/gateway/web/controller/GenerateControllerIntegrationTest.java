/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/
package com.example.api.gateway.web.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.api.gateway.config.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.wiremock.integrations.testcontainers.WireMockContainer;

class GenerateControllerIntegrationTest extends AbstractIntegrationTest {

    @Container
    static final WireMockContainer wireMockServer =
        new WireMockContainer("wiremock/wiremock:latest-alpine");

    static {
        wireMockServer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gateway.routes[0].uri", wireMockServer::getBaseUrl);
        registry.add("spring.cloud.gateway.routes[0].id", () -> "catalog-service");
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/catalog-service/**");

        registry.add("spring.cloud.gateway.routes[1].uri", wireMockServer::getBaseUrl);
        registry.add("spring.cloud.gateway.routes[1].id", () -> "inventory-service");
        registry.add("spring.cloud.gateway.routes[1].predicates[0]", () -> "Path=/inventory-service/**");

        // Short delay for faster tests
        registry.add("test.wiremock.delay-seconds", () -> "1");
    }

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();

        // Catalog-service stub
        wireMockServer.stubFor(
            get(urlEqualTo("/catalog-service/api/catalog/generate"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/plain")
                    .withBody("Catalog data generated successfully")));

        // Inventory-service stub
        wireMockServer.stubFor(
            get(urlEqualTo("/inventory-service/api/inventory/generate"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/plain")
                    .withBody("Inventory data generated successfully")));
    }

    @Test
    void shouldReturnSuccessResponseWhenBothServicesSucceed() {
        System.setProperty("test.wiremock.delay-seconds", "1");
        try {
            webTestClient.get()
                .uri("/api/generate")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> {
                    String body = result.getResponseBody();
                    assertThat(body).contains("Generation process completed successfully");
                    assertThat(body).contains("Catalog data generated successfully");
                    assertThat(body).contains("Inventory data generated successfully");
                });
            wireMockServer.verify(1, getRequestedFor(urlEqualTo("/catalog-service/api/catalog/generate")));
            wireMockServer.verify(1, getRequestedFor(urlEqualTo("/inventory-service/api/inventory/generate")));
        } finally {
            System.clearProperty("test.wiremock.delay-seconds");
        }
    }

    @Test
    void shouldHandleCatalogServiceError() {
        // Stub catalog error
        wireMockServer.stubFor(
            get(urlEqualTo("/catalog-service/api/catalog/generate"))
                .willReturn(aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "text/plain")
                    .withBody("Internal Server Error")));
        System.setProperty("test.wiremock.delay-seconds", "1");
        try {
            webTestClient.get()
                .uri("/api/generate")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> {
                    String body = result.getResponseBody();
                    assertThat(body).contains("Error calling catalog service:");
                    assertThat(body).contains("Inventory data generated successfully");
                });
        } finally {
            System.clearProperty("test.wiremock.delay-seconds");
        }
    }

    @Test
    void shouldHandleInventoryServiceError() {
        // Stub inventory error
        wireMockServer.stubFor(
            get(urlEqualTo("/inventory-service/api/inventory/generate"))
                .willReturn(aResponse()
                    .withStatus(500)
                    .withHeader("Content-Type", "text/plain")
                    .withBody("Internal Server Error")));
        System.setProperty("test.wiremock.delay-seconds", "1");
        try {
            webTestClient.get()
                .uri("/api/generate")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .consumeWith(result -> {
                    String body = result.getResponseBody();
                    assertThat(body).contains("Catalog data generated successfully");
                    assertThat(body).contains("Error calling inventory service:");
                });
        } finally {
            System.clearProperty("test.wiremock.delay-seconds");
        }
    }

    @Test
    void shouldModifyDelayDurationForTesting() {
        System.setProperty("test.wiremock.delay-seconds", "1");
        try {
            webTestClient.get()
                .uri("/api/generate")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
            wireMockServer.verify(1, getRequestedFor(urlEqualTo("/catalog-service/api/catalog/generate")));
            wireMockServer.verify(1, getRequestedFor(urlEqualTo("/inventory-service/api/inventory/generate")));
        } finally {
            System.clearProperty("test.wiremock.delay-seconds");
        }
    }
}