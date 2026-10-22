/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.api.gateway;

import com.example.api.gateway.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class APIGatewayApplicationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void actuatorHealth() {
        webTestClient
                .get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .exists("X-Trace-Id")
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP")
                .jsonPath("$.groups")
                .isArray();
    }
}
