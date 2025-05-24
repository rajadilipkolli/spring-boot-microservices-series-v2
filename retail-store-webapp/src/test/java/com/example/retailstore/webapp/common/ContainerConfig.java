package com.example.retailstore.webapp.common;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class ContainerConfig {

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.2.4";
    private static final String REALM_IMPORT_FILE = "/docker/realm-config/retailstore-realm.json";
    private static final String REALM_NAME = "retailstore";

    @Bean
    KeycloakContainer keycloak() {
        return new KeycloakContainer(KEYCLOAK_IMAGE)
                .withAdminPassword("junitAdmin")
                .withAdminPassword("junitPasscode")
                .withRealmImportFile(REALM_IMPORT_FILE);
    }

    @Bean
    DynamicPropertyRegistrar dynamicPropertyRegistrar(KeycloakContainer keycloak) {
        return registry -> {
            registry.add("OAUTH2_SERVER_URL", keycloak::getAuthServerUrl);
        };
    }
}
