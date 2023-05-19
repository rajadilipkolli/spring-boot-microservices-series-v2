/* Licensed under Apache-2.0 2021-2023 */
package com.example.api.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.cloud.discovery.reactive.enabled=false",
            "spring.cloud.discovery.enabled=false",
            "logging.file.name=logs/api-gateway.log"
        })
@AutoConfigureWebClient
@ActiveProfiles("test")
class APIGatewayApplicationTest {

    private static final int CONFIG_SERVER_INTERNAL_PORT = 8888;

    static final ConfigServerContainer CONFIG_SERVER_CONTAINER =
            new ConfigServerContainer(
                            DockerImageName.parse("dockertmt/mmv2-config-server-17:0.0.1-SNAPSHOT"))
                    .withExposedPorts(CONFIG_SERVER_INTERNAL_PORT);

    @Container
    @ServiceConnection(name = "openzipkin/zipkin")
    static final ZipkinContainer ZIPKIN_CONTAINER =
            new ZipkinContainer(DockerImageName.parse("openzipkin/zipkin"));

    static {
        Startables.deepStart(CONFIG_SERVER_CONTAINER).join();
    }

    @DynamicPropertySource
    static void registerApplicationProperties(DynamicPropertyRegistry propertyRegistry) {
        propertyRegistry.add(
                "spring.config.import",
                () ->
                        String.format(
                                "optional:configserver:http://%s:%d/",
                                CONFIG_SERVER_CONTAINER.getHost(),
                                CONFIG_SERVER_CONTAINER.getMappedPort(
                                        CONFIG_SERVER_INTERNAL_PORT)));
    }

    @Autowired private WebTestClient webTestClient;

    @Test
    void contextLoads() {
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

    private static class ConfigServerContainer extends GenericContainer<ConfigServerContainer> {

        public ConfigServerContainer(final DockerImageName dockerImageName) {
            super(dockerImageName);
        }
    }

    private static class ZipkinContainer extends GenericContainer<ZipkinContainer> {
        public ZipkinContainer(DockerImageName dockerImageName) {
            super(dockerImageName);
        }
    }
}
