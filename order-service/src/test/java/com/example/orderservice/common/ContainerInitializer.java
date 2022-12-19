/* Licensed under Apache-2.0 2022 */
package com.example.orderservice.common;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

public class ContainerInitializer {
    private static final int CONFIG_SERVER_INTERNAL_PORT = 8888;
    private static final int NAMING_SERVER_INTERNAL_PORT = 8761;
    private static final int ZIPKIN_INTERNAL_PORT = 9411;

    protected static final KafkaContainer KAFKA_CONTAINER =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.1"));

    protected static final PostgreSQLContainer<?> POSTGRE_SQL_CONTAINER =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("integration-tests-db")
                    .withUsername("username")
                    .withPassword("password");

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
                        CONFIG_SERVER_CONTAINER,
                        NAMING_SERVER_CONTAINER,
                        POSTGRE_SQL_CONTAINER,
                        ZIPKIN_CONTAINER,
                        KAFKA_CONTAINER)
                .join();
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

    @DynamicPropertySource
    static void addApplicationProperties(DynamicPropertyRegistry propertyRegistry) {
        propertyRegistry.add("spring.datasource.url", POSTGRE_SQL_CONTAINER::getJdbcUrl);
        propertyRegistry.add("spring.datasource.username", POSTGRE_SQL_CONTAINER::getUsername);
        propertyRegistry.add("spring.datasource.password", POSTGRE_SQL_CONTAINER::getPassword);
        propertyRegistry.add(
                "spring.kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);
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
}
