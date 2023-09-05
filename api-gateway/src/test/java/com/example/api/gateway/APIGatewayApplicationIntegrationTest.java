/* Licensed under Apache-2.0 2021-2023 */
package com.example.api.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.api.gateway.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class APIGatewayApplicationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void testActuatorHealth() {
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

    @Test
    void contextLoads() {
        webTestClient.get().uri("/").exchange().expectStatus().isTemporaryRedirect();
    }
}
