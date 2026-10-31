/***
<p>
    Licensed under MIT License Copyright (c) 2025-2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.api.gateway.config.AbstractIntegrationTest;
import com.example.api.gateway.model.GenerationResponse;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.core.publisher.Flux;

@Import(GenerateControllerIntegrationTest.TestLoadBalancerConfig.class)
class GenerateControllerIntegrationTest extends AbstractIntegrationTest {

    private WireMock wireMock;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.delay-between-services", () -> "10ms");
        registry.add("gateway.workflow-timeout", () -> "10s");
        registry.add("gateway.request-timeout", () -> "3s");
        registry.add("gateway.response-timeout", () -> "3s");
    }

    @TestConfiguration(proxyBeanMethods = false)
    @LoadBalancerClients
    static class TestLoadBalancerConfig {

        @Bean
        ServiceInstanceListSupplier serviceInstanceListSupplier() {
            return new ServiceInstanceListSupplier() {
                @Override
                public String getServiceId() {
                    return "MICROSERVICES";
                }

                @Override
                public Flux<List<ServiceInstance>> get() {
                    ServiceInstance instance =
                            new DefaultServiceInstance(
                                    "instance-1",
                                    getServiceId(),
                                    wireMockServer.getHost(),
                                    wireMockServer.getPort(),
                                    false);
                    return Flux.just(List.of(instance));
                }
            };
        }
    }

    @BeforeEach
    void setUp() {
        wireMock = new WireMock(wireMockServer.getHost(), wireMockServer.getPort());
        wireMock.resetRequests();
        wireMock.resetMappings();
    }

    @Test
    void shouldOrchestrateGenerationSuccessfullyWithoutBatchSize() {
        wireMock.register(
                post(urlEqualTo("/catalog-service/api/catalog/generate"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("Catalog generated successfully")));

        wireMock.register(
                post(urlEqualTo("/inventory-service/api/inventory/generate"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("Inventory generated successfully")));

        wireMock.register(
                post(urlEqualTo("/order-service/api/orders/generate"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("Orders generated successfully")));

        webTestClient
                .post()
                .uri("/api/v1/generate")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_JSON)
                .expectBody(GenerationResponse.class)
                .consumeWith(
                        result -> {
                            GenerationResponse response = result.getResponseBody();
                            assertThat(response).isNotNull();
                            assertThat(response.status()).isEqualTo("success");
                            assertThat(response.message())
                                    .isEqualTo("Generation process completed successfully");
                            assertThat(response.serviceResponses())
                                    .containsEntry("catalog", "Catalog generated successfully")
                                    .containsEntry("inventory", "Inventory generated successfully")
                                    .containsEntry("order", "Orders generated successfully");
                        });

        wireMock.verifyThat(
                1,
                postRequestedFor(urlEqualTo("/catalog-service/api/catalog/generate"))
                        .withHeader("Idempotency-Key", matching("[a-f0-9\\-]{36}")));

        wireMock.verifyThat(
                1,
                postRequestedFor(urlEqualTo("/inventory-service/api/inventory/generate"))
                        .withHeader("Idempotency-Key", matching("[a-f0-9\\-]{36}")));

        wireMock.verifyThat(
                1,
                postRequestedFor(urlEqualTo("/order-service/api/orders/generate"))
                        .withHeader("Idempotency-Key", matching("[a-f0-9\\-]{36}")));
    }

    @Test
    void shouldOrchestrateGenerationSuccessfullyWithValidBatchSize() {
        wireMock.register(
                post(urlEqualTo("/catalog-service/api/catalog/generate?batchSize=50"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("50 items generated")));

        wireMock.register(
                post(urlEqualTo("/inventory-service/api/inventory/generate?batchSize=50"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("50 inventories created")));

        wireMock.register(
                post(urlEqualTo("/order-service/api/orders/generate?batchSize=50"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("50 orders placed")));

        webTestClient
                .post()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/api/v1/generate")
                                        .queryParam("batchSize", 50)
                                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(GenerationResponse.class)
                .consumeWith(
                        result -> {
                            GenerationResponse response = result.getResponseBody();
                            assertThat(response).isNotNull();
                            assertThat(response.status()).isEqualTo("success");
                            assertThat(response.serviceResponses())
                                    .containsEntry("catalog", "50 items generated")
                                    .containsEntry("inventory", "50 inventories created")
                                    .containsEntry("order", "50 orders placed");
                        });

        wireMock.verifyThat(
                1,
                postRequestedFor(urlEqualTo("/catalog-service/api/catalog/generate?batchSize=50")));
        wireMock.verifyThat(
                1,
                postRequestedFor(
                        urlEqualTo("/inventory-service/api/inventory/generate?batchSize=50")));
        wireMock.verifyThat(
                1, postRequestedFor(urlEqualTo("/order-service/api/orders/generate?batchSize=50")));
    }

    @Test
    void shouldReturnBadGatewayWhenCatalogServiceFails() {
        wireMock.register(
                post(urlPathEqualTo("/catalog-service/api/catalog/generate"))
                        .willReturn(
                                aResponse()
                                        .withStatus(500)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("Catalog DB down")));

        webTestClient
                .post()
                .uri("/api/v1/generate")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectBody(GenerationResponse.class)
                .consumeWith(
                        result -> {
                            GenerationResponse response = result.getResponseBody();
                            assertThat(response).isNotNull();
                            assertThat(response.status()).isEqualTo("error");
                            assertThat(response.message())
                                    .contains("Generation failed in catalog service");
                        });

        wireMock.verifyThat(
                0, postRequestedFor(urlPathEqualTo("/inventory-service/api/inventory/generate")));
        wireMock.verifyThat(
                0, postRequestedFor(urlPathEqualTo("/order-service/api/orders/generate")));
    }

    @Test
    void shouldReturnBadGatewayWhenInventoryServiceFails() {
        wireMock.register(
                post(urlPathEqualTo("/catalog-service/api/catalog/generate"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("Catalog OK")));

        wireMock.register(
                post(urlPathEqualTo("/inventory-service/api/inventory/generate"))
                        .willReturn(
                                aResponse()
                                        .withStatus(500)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("Inventory DB error")));

        webTestClient
                .post()
                .uri("/api/v1/generate")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectBody(GenerationResponse.class)
                .consumeWith(
                        result -> {
                            GenerationResponse response = result.getResponseBody();
                            assertThat(response).isNotNull();
                            assertThat(response.status()).isEqualTo("error");
                            assertThat(response.message())
                                    .contains("Generation failed in inventory service");
                        });

        wireMock.verifyThat(
                1, postRequestedFor(urlPathEqualTo("/catalog-service/api/catalog/generate")));
        wireMock.verifyThat(
                0, postRequestedFor(urlPathEqualTo("/order-service/api/orders/generate")));
    }

    @Test
    void shouldReturnBadGatewayWhenOrderServiceFails() {
        wireMock.register(
                post(urlPathEqualTo("/catalog-service/api/catalog/generate"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("Catalog OK")));

        wireMock.register(
                post(urlPathEqualTo("/inventory-service/api/inventory/generate"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("Inventory OK")));

        wireMock.register(
                post(urlPathEqualTo("/order-service/api/orders/generate"))
                        .willReturn(
                                aResponse()
                                        .withStatus(500)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("Order service failed")));

        webTestClient
                .post()
                .uri("/api/v1/generate")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.BAD_GATEWAY)
                .expectBody(GenerationResponse.class)
                .consumeWith(
                        result -> {
                            GenerationResponse response = result.getResponseBody();
                            assertThat(response).isNotNull();
                            assertThat(response.status()).isEqualTo("error");
                            assertThat(response.message())
                                    .contains("Generation failed in order service");
                        });

        wireMock.verifyThat(
                1, postRequestedFor(urlPathEqualTo("/catalog-service/api/catalog/generate")));
        wireMock.verifyThat(
                1, postRequestedFor(urlPathEqualTo("/inventory-service/api/inventory/generate")));
    }

    @Test
    void shouldReturnServiceUnavailableWhenDownstreamReturns429() {
        wireMock.register(
                post(urlPathEqualTo("/catalog-service/api/catalog/generate"))
                        .willReturn(
                                aResponse()
                                        .withStatus(429)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("Rate limited")));

        webTestClient
                .post()
                .uri("/api/v1/generate")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody(GenerationResponse.class)
                .consumeWith(
                        result -> {
                            GenerationResponse response = result.getResponseBody();
                            assertThat(response).isNotNull();
                            assertThat(response.status()).isEqualTo("error");
                            assertThat(response.message())
                                    .contains("Generation failed in catalog service");
                        });
    }

    @Test
    void shouldHandleDownstreamEmptyBodySuccessfully() {
        wireMock.register(
                post(urlEqualTo("/catalog-service/api/catalog/generate"))
                        .willReturn(aResponse().withStatus(200)));

        wireMock.register(
                post(urlEqualTo("/inventory-service/api/inventory/generate"))
                        .willReturn(aResponse().withStatus(200)));

        wireMock.register(
                post(urlEqualTo("/order-service/api/orders/generate"))
                        .willReturn(aResponse().withStatus(200)));

        webTestClient
                .post()
                .uri("/api/v1/generate")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(GenerationResponse.class)
                .consumeWith(
                        result -> {
                            GenerationResponse response = result.getResponseBody();
                            assertThat(response).isNotNull();
                            assertThat(response.status()).isEqualTo("success");
                            assertThat(response.serviceResponses())
                                    .containsEntry("catalog", "")
                                    .containsEntry("inventory", "")
                                    .containsEntry("order", "");
                        });
    }

    @Test
    void shouldRetryOnTransientDownstream503ThenSucceed() {
        wireMock.register(
                post(urlEqualTo("/catalog-service/api/catalog/generate"))
                        .inScenario("RetryScenario")
                        .whenScenarioStateIs(
                                com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                        .willReturn(aResponse().withStatus(503).withBody("Temporary failure"))
                        .willSetStateTo("RetriedOnce"));

        wireMock.register(
                post(urlEqualTo("/catalog-service/api/catalog/generate"))
                        .inScenario("RetryScenario")
                        .whenScenarioStateIs("RetriedOnce")
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("Catalog recovered")));

        wireMock.register(
                post(urlEqualTo("/inventory-service/api/inventory/generate"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("Inventory OK")));

        wireMock.register(
                post(urlEqualTo("/order-service/api/orders/generate"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withHeader("Content-Type", "text/plain")
                                        .withBody("Order OK")));

        webTestClient
                .post()
                .uri("/api/v1/generate")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(GenerationResponse.class)
                .consumeWith(
                        result -> {
                            GenerationResponse response = result.getResponseBody();
                            assertThat(response).isNotNull();
                            assertThat(response.status()).isEqualTo("success");
                            assertThat(response.serviceResponses().get("catalog"))
                                    .isEqualTo("Catalog recovered");
                        });

        wireMock.verifyThat(
                2, postRequestedFor(urlEqualTo("/catalog-service/api/catalog/generate")));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -100, 10_001, 50_000})
    void shouldReturnBadRequestForInvalidBatchSize(int batchSize) {
        webTestClient
                .post()
                .uri(
                        uriBuilder ->
                                uriBuilder
                                        .path("/api/v1/generate")
                                        .queryParam("batchSize", batchSize)
                                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(400)
                .jsonPath("$.title")
                .isEqualTo("Bad Request");

        wireMock.verifyThat(
                0, postRequestedFor(urlPathEqualTo("/catalog-service/api/catalog/generate")));
    }
}
