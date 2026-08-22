/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class CacheAndTransformationIntegrationTest extends AbstractIntegrationTest {

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.cloud.gateway.server.webflux.filter.local-response-cache.enabled",
                () -> "true");
        // Route 0 for Cache
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].uri", wireMockServer::getBaseUrl);
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].id", () -> "catalog-service-cache");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].predicates[0]",
                () -> "Path=/catalog-service-cache/**");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].filters[0].name",
                () -> "LocalResponseCache");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].filters[0].args.size", () -> "10MB");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[0].filters[0].args.timeToLive",
                () -> "1m");

        // Route 1 for Transformation
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[1].uri", wireMockServer::getBaseUrl);
        registry.add("spring.cloud.gateway.server.webflux.routes[1].id", () -> "transform-service");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[1].predicates[0]",
                () -> "Path=/transform-service/**");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[1].filters[0].name",
                () -> "RewritePath");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[1].filters[0].args.regexp",
                () -> "/transform-service/(?<segment>.*)");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[1].filters[0].args.replacement",
                () -> "/${segment}");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[1].filters[1].name",
                () -> "AddResponseHeader");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[1].filters[1].args.name",
                () -> "X-Transformed");
        registry.add(
                "spring.cloud.gateway.server.webflux.routes[1].filters[1].args.value",
                () -> "true");
    }

    @RepeatedTest(value = 3)
    void catalogServiceShouldCacheResponse() {
        webTestClient
                .get()
                .uri("/catalog-service-cache/api/cacheable")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .consumeWith(
                        result -> assertThat(result.getResponseBody()).contains("cached response"));
    }

    @Test
    void shouldTransformPathAndHeaders() {
        webTestClient
                .get()
                .uri("/transform-service/api/transformable")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals("X-Transformed", "true")
                .expectBody(String.class)
                .consumeWith(
                        result ->
                                assertThat(result.getResponseBody())
                                        .contains("transformed response"));
    }
}
