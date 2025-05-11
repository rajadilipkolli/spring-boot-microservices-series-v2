package com.example.retailstore.webapp.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class KeycloakTestContainer {

    @Bean
    @ServiceConnection(name = "keycloak")
    GenericContainer<?> keycloakContainer() {
        return new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.2.4"))
                .withExposedPorts(9191)
                .withCommand("start-dev", "--import-realm", "--http-port=9191")
                .withEnv("KEYCLOAK_ADMIN", "admin")
                .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin1234")
                .withReuse(true);
    }
}
