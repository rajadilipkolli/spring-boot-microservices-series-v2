package com.example.catalogservice.common;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

public abstract class ContainerInitializer {

    private static final int CONFIG_SERVER_INTERNAL_PORT = 8888;
    private static final int ZIPKIN_INTERNAL_PORT = 9411;

    protected static final KafkaContainer KAFKA_CONTAINER =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.1"));

    public static final PostgreSQLContainer<?> POSTGRE_SQL_CONTAINER =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("integration-tests-db")
                    .withUsername("username")
                    .withPassword("password");

    public static final ConfigServerContainer CONFIG_SERVER_CONTAINER =
            new ConfigServerContainer(
                            DockerImageName.parse("dockertmt/mmv2-config-server-17:0.0.1-SNAPSHOT"))
                    .withEnv("SPRING_PROFILES_ACTIVE", "native")
                    .withExposedPorts(CONFIG_SERVER_INTERNAL_PORT);

    public static final ZipkinContainer ZIPKIN_CONTAINER =
            new ZipkinContainer(DockerImageName.parse("openzipkin/zipkin-slim"))
                    .withExposedPorts(ZIPKIN_INTERNAL_PORT);

    static {
        Startables.deepStart(
                        KAFKA_CONTAINER,
                        CONFIG_SERVER_CONTAINER,
                        POSTGRE_SQL_CONTAINER,
                        ZIPKIN_CONTAINER)
                .join();
    }

    @DynamicPropertySource
    public static void registerApplicationProperties(
            DynamicPropertyRegistry dynamicPropertyRegistry) {
        dynamicPropertyRegistry.add("spring.datasource.url", POSTGRE_SQL_CONTAINER::getJdbcUrl);
        dynamicPropertyRegistry.add(
                "spring.datasource.username", POSTGRE_SQL_CONTAINER::getUsername);
        dynamicPropertyRegistry.add(
                "spring.datasource.password", POSTGRE_SQL_CONTAINER::getPassword);
        dynamicPropertyRegistry.add(
                "spring.kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);
        dynamicPropertyRegistry.add(
                "spring.config.import",
                () ->
                        String.format(
                                "configserver:http://%s:%d/",
                                CONFIG_SERVER_CONTAINER.getHost(),
                                CONFIG_SERVER_CONTAINER.getMappedPort(
                                        CONFIG_SERVER_INTERNAL_PORT)));
        dynamicPropertyRegistry.add(
                "spring.zipkin.baseurl",
                () ->
                        String.format(
                                "http://%s:%d/",
                                ZIPKIN_CONTAINER.getHost(),
                                ZIPKIN_CONTAINER.getMappedPort(ZIPKIN_INTERNAL_PORT)));
    }

    private static class ConfigServerContainer extends GenericContainer<ConfigServerContainer> {
        public ConfigServerContainer(final DockerImageName dockerImageName) {
            super(dockerImageName);
        }
    }

    private static class ServiceRegistryContainer
            extends GenericContainer<ServiceRegistryContainer> {
        public ServiceRegistryContainer(DockerImageName dockerImageName) {
            super(dockerImageName);
        }
    }

    private static class ZipkinContainer extends GenericContainer<ZipkinContainer> {
        public ZipkinContainer(DockerImageName dockerImageName) {
            super(dockerImageName);
        }
    }
}
