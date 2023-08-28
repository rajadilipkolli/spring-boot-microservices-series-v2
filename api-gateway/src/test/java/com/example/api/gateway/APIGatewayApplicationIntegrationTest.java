/* Licensed under Apache-2.0 2021-2023 */
package com.example.api.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.cloud.discovery.reactive.enabled=false",
            "spring.cloud.discovery.enabled=false",
            "spring.cloud.config.enabled=false",
            "logging.file.name=logs/api-gateway.log"
        },
        classes = TestAPIGatewayApplication.class)
@AutoConfigureWebClient
@ActiveProfiles("test")
class APIGatewayApplicationIntegrationTest {

    @Autowired private WebTestClient webTestClient;

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
