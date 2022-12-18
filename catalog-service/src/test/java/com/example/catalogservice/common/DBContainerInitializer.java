package com.example.catalogservice.common;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

public class DBContainerInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final PostgreSQLContainer<?> POSTGRE_SQL_CONTAINER =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("integration-tests-db")
                    .withUsername("username")
                    .withPassword("password");

    private static final int CONFIG_SERVER_INTERNAL_PORT = 8888;

    public static final ConfigServerContainer CONFIG_SERVER_CONTAINER =
            new ConfigServerContainer(
                            DockerImageName.parse("dockertmt/mmv2-config-server-17:0.0.1-SNAPSHOT"))
                    .withEnv("SPRING_PROFILES_ACTIVE", "native")
                    .withExposedPorts(CONFIG_SERVER_INTERNAL_PORT);

    static {
        Startables.deepStart(CONFIG_SERVER_CONTAINER, POSTGRE_SQL_CONTAINER).join();
    }

    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        TestPropertyValues.of(
                        "spring.datasource.url=" + POSTGRE_SQL_CONTAINER.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRE_SQL_CONTAINER.getUsername(),
                        "spring.datasource.password=" + POSTGRE_SQL_CONTAINER.getPassword(),
                        "spring.config.import="
                                + String.format(
                                        "configserver:http://%s:%d/",
                                        CONFIG_SERVER_CONTAINER.getHost(),
                                        CONFIG_SERVER_CONTAINER.getMappedPort(
                                                CONFIG_SERVER_INTERNAL_PORT)))
                .applyTo(configurableApplicationContext.getEnvironment());
    }

    private static class ConfigServerContainer extends GenericContainer<ConfigServerContainer> {
        public ConfigServerContainer(final DockerImageName dockerImageName) {
            super(dockerImageName);
        }
    }
}
