package com.example.retailstore.webapp.common;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

@TestConfiguration(proxyBeanMethods = false)
public class ContainerConfig {

    static String KEYCLOAK_IMAGE = "quay.io/keycloak/keycloak:26.2.4";
    static String realmImportFile = "/docker/realm-config/retailstore-realm.json";
    static String realmName = "retailstore";

    @Bean
    KeycloakContainer keycloak() {
        return new KeycloakContainer(KEYCLOAK_IMAGE).withRealmImportFile(realmImportFile);
    }

    @Bean
    DynamicPropertyRegistrar dynamicPropertyRegistrar(KeycloakContainer keycloak) {
        return registry -> {
            registry.add(
                    "spring.security.oauth2.resourceserver.jwt.issuer-uri",
                    () -> keycloak.getAuthServerUrl() + "/realms/" + realmName);
        };
    }
}
