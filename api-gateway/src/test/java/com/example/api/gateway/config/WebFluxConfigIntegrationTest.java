/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ORIGIN;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class WebFluxConfigIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldAllowCorsForConfiguredOrigin() {
        webTestClient
                .options()
                .uri("/test-path")
                .header(ORIGIN, "http://localhost:8765")
                .header("Access-Control-Request-Method", HttpMethod.GET.name())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals(ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:8765")
                .expectHeader()
                .valueEquals(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectHeader()
                .exists("X-Trace-Id");
    }

    @Test
    void shouldNotAllowCorsForUnconfiguredOrigin() {
        webTestClient
                .options()
                .uri("/test-path")
                .header(ORIGIN, "http://unknown-origin:8080")
                .header("Access-Control-Request-Method", HttpMethod.GET.name())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectHeader()
                .exists("X-Trace-Id");
    }

    @Test
    void shouldAllowConfiguredMethods() {
        webTestClient
                .options()
                .uri("/test-path")
                .header(ORIGIN, "http://localhost:8765")
                .header("Access-Control-Request-Method", HttpMethod.PUT.name())
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .exists("X-Trace-Id");
    }

    @Test
    void shouldNotAllowUnconfiguredMethods() {
        webTestClient
                .options()
                .uri("/test-path")
                .header(ORIGIN, "http://localhost:8765")
                .header("Access-Control-Request-Method", HttpMethod.DELETE.name())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectHeader()
                .doesNotExist(ACCESS_CONTROL_ALLOW_ORIGIN)
                .expectHeader()
                .exists("X-Trace-Id");
    }
}
