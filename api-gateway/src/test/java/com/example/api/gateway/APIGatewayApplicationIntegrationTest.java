/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.api.gateway;

import static org.assertj.core.api.Assertions.assertThat;

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
                .expectBody(String.class)
                .consumeWith(
                        res -> assertThat(res.getResponseBody()).isEqualTo("{\"status\":\"UP\"}"));
    }
}
