/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.wiremock.integrations.testcontainers.WireMockContainer;

class ApiGatewayConfigurationIntegrationTest extends AbstractIntegrationTest {

    @Container
    static final WireMockContainer wireMockServer =
            new WireMockContainer("wiremock/wiremock:latest-alpine")
                    .withMappingFromResource(
                            "test-routing",
                            ApiGatewayConfigurationIntegrationTest.class,
                            ApiGatewayConfigurationIntegrationTest.class.getSimpleName()
                                    + "/test-routing.json")
                    .withMappingFromResource(
                            "get-mapping",
                            ApiGatewayConfigurationIntegrationTest.class,
                            ApiGatewayConfigurationIntegrationTest.class.getSimpleName()
                                    + "/get-mapping.json");

    static {
        wireMockServer.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].uri", wireMockServer::getBaseUrl);
        registry.add("spring.cloud.gateway.server.webflux.routes[0].id", () -> "test-service");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].predicates[0]",
                () -> "Path=/test-service/**");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].filters[0].name",
                () -> "RewritePath");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].filters[0].args.regexp",
                () -> "/test-service/(?<segment>.*)");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].filters[0].args.replacement",
                () -> "/${segment}");
        registry.add("app.gateway.httpbin", wireMockServer::getBaseUrl);
    }

    @Test
    void shouldRouteRequestWithRewritePath() {
        webTestClient
                .get()
                .uri("/test-service/example")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .consumeWith(
                        result -> {
                            assertThat(result.getResponseBody()).contains("test route response");
                        });
    }

    @Test
    void shouldAddRequestHeader() {
        webTestClient
                .get()
                .uri("/get")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.headers.Myheader")
                .isEqualTo("MyURI");
    }

    @Test
    void shouldAddRequestParameter() {
        webTestClient
                .get()
                .uri("/get")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.args.Param")
                .isEqualTo("MyValue");
    }

    @Test
    void shouldNotAddRequestParameterForNonMatchingPath() {
        webTestClient
                .get()
                .uri("/different-path")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody()
                .jsonPath("$.args.Param")
                .doesNotExist();
    }
}
