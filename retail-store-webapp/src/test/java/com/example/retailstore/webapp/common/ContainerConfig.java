package com.example.retailstore.webapp.common;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class ContainerConfig {

    private static final String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.3.4";
    private static final String REALM_IMPORT_FILE = "/docker/realm-config/retailstore-realm.json";
    private static final String REALM_NAME = "retailstore";

    @Bean
    KeycloakContainer keycloak() {
        return new KeycloakContainer(KEYCLOAK_IMAGE)
                .withAdminUsername("junitAdmin")
                .withAdminPassword("junitPasscode")
                .withRealmImportFile(REALM_IMPORT_FILE);
    }

    @Bean
    DynamicPropertyRegistrar dynamicPropertyRegistrar(KeycloakContainer keycloak) {
        return registry -> {
            registry.add("OAUTH2_SERVER_URL", keycloak::getAuthServerUrl);
            registry.add("keycloak.server-url", keycloak::getAuthServerUrl);
            registry.add(
                    "spring.security.oauth2.client.provider.keycloak.issuer-uri",
                    () -> keycloak.getAuthServerUrl() + "/realms/" + REALM_NAME);

            // Properties for KeycloakProperties binding, used by KeycloakRegistrationService
            registry.add("keycloak.realm", () -> REALM_NAME); // REALM_NAME is "retailstore"

            // For admin operations, KeycloakRegistrationService targets the "master" realm's token
            // endpoint.
            // Use the container's admin credentials for the master realm.
            registry.add("keycloak.admin-username", keycloak::getAdminUsername);
            registry.add("keycloak.admin-password", keycloak::getAdminPassword);
            registry.add("keycloak.admin-client-id", () -> "admin-cli"); // Common client ID in master realm
            registry.add("keycloak.admin-client-secret", () -> ""); // Assuming admin-cli is public or secret is empty
        };
    }
}
