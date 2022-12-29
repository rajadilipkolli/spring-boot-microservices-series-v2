/* Licensed under Apache-2.0 2021-2022 */
package com.example.api.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebClient
@ActiveProfiles("test")
class APIGatewayApplicationTest {

    private static final int CONFIG_SERVER_INTERNAL_PORT = 8888;
    private static final int NAMING_SERVER_INTERNAL_PORT = 8761;
    private static final int ZIPKIN_INTERNAL_PORT = 9411;

    static final MongoDBContainer MONGO_DB_CONTAINER =
            new MongoDBContainer(DockerImageName.parse("mongo:6.0.3"));

    static final NamingServerContainer NAMING_SERVER_CONTAINER =
            new NamingServerContainer(
                            DockerImageName.parse(
                                    "dockertmt/mmv2-service-registry-17:0.0.1-SNAPSHOT"))
                    .withExposedPorts(NAMING_SERVER_INTERNAL_PORT);

    static final ConfigServerContainer CONFIG_SERVER_CONTAINER =
            new ConfigServerContainer(
                            DockerImageName.parse("dockertmt/mmv2-config-server-17:0.0.1-SNAPSHOT"))
                    .withExposedPorts(CONFIG_SERVER_INTERNAL_PORT);

    static final ZipkinContainer ZIPKIN_CONTAINER =
            new ZipkinContainer(DockerImageName.parse("openzipkin/zipkin-slim"))
                    .withExposedPorts(ZIPKIN_INTERNAL_PORT);

    static {
        Startables.deepStart(
                        MONGO_DB_CONTAINER,
                        NAMING_SERVER_CONTAINER,
                        CONFIG_SERVER_CONTAINER,
                        ZIPKIN_CONTAINER)
                .join();
    }

    @DynamicPropertySource
    static void registerApplicationProperties(DynamicPropertyRegistry propertyRegistry) {
        propertyRegistry.add("spring.data.mongodb.uri", MONGO_DB_CONTAINER::getReplicaSetUrl);
        propertyRegistry.add(
                "spring.config.import",
                () ->
                        String.format(
                                "optional:configserver:http://%s:%d/",
                                CONFIG_SERVER_CONTAINER.getHost(),
                                CONFIG_SERVER_CONTAINER.getMappedPort(
                                        CONFIG_SERVER_INTERNAL_PORT)));
        propertyRegistry.add(
                "eureka.client.serviceUrl.defaultZone",
                () ->
                        String.format(
                                "http://%s:%d/eureka/",
                                NAMING_SERVER_CONTAINER.getHost(),
                                NAMING_SERVER_CONTAINER.getMappedPort(
                                        NAMING_SERVER_INTERNAL_PORT)));
        propertyRegistry.add(
                "spring.zipkin.baseurl",
                () ->
                        String.format(
                                "http://%s:%d/",
                                ZIPKIN_CONTAINER.getHost(),
                                ZIPKIN_CONTAINER.getMappedPort(ZIPKIN_INTERNAL_PORT)));
    }

    @Autowired private WebTestClient webTestClient;

    @Test
    void contextLoads() {
        assertThat(MONGO_DB_CONTAINER.isRunning()).isTrue();
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

    private static class NamingServerContainer extends GenericContainer<NamingServerContainer> {

        public NamingServerContainer(final DockerImageName dockerImageName) {
            super(dockerImageName);
        }
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
