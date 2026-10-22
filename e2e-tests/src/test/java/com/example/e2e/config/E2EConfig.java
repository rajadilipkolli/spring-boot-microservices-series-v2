/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.e2e.config;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class E2EConfig {

    private static final Logger log = LoggerFactory.getLogger(E2EConfig.class);

    private static final String DEFAULT_BASE_URL = "http://localhost:8765";
    private static final String DEFAULT_KEYCLOAK_URL = "http://localhost:9191";
    private static final String DEFAULT_WEBAPP_URL = "http://localhost:8080";

    private E2EConfig() {}

    public static String getBaseUrl() {
        String sysProp = System.getProperty("base.url");
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }
        String env = System.getenv("BASE_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String host = System.getenv("HOST");
        String port = System.getenv("PORT");
        if (host != null && !host.isBlank()) {
            return "http://" + host + (port != null && !port.isBlank() ? ":" + port : "");
        }
        return DEFAULT_BASE_URL;
    }

    public static String getKeycloakUrl() {
        String sysProp = System.getProperty("keycloak.url");
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }
        String env = System.getenv("KEYCLOAK_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return DEFAULT_KEYCLOAK_URL;
    }

    public static String getKeycloakClientSecret() {
        return getCredential(
                "keycloak.client.secret",
                "TF_VAR_webapp_client_secret",
                "demo-throwaway-oauth-secret");
    }

    public static String getKeycloakPassword() {
        return getCredential("keycloak.password", "KEYCLOAK_RETAIL_PASSWORD", "retail1234");
    }

    private static String getCredential(
            String property, String environmentVariable, String fallback) {
        String sysProp = System.getProperty(property);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp;
        }
        String env = System.getenv(environmentVariable);
        return env != null && !env.isBlank() ? env : fallback;
    }

    public static String getWebappUrl() {
        String sysProp = System.getProperty("webapp.url");
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }
        String env = System.getenv("WEBAPP_URL");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return DEFAULT_WEBAPP_URL;
    }

    public static RequestSpecification defaultRequestSpec() {
        return new RequestSpecBuilder()
                .setBaseUri(getBaseUrl())
                .setConfig(restAssuredConfig())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();
    }

    private static RestAssuredConfig restAssuredConfig() {
        SSLConfig sslConfig = SSLConfig.sslConfig().strictHostnames();
        String trustStore = System.getProperty("e2e.trustStore");
        if (trustStore != null && !trustStore.isBlank()) {
            sslConfig =
                    sslConfig
                            .trustStoreType(System.getProperty("e2e.trustStoreType", "PKCS12"))
                            .trustStore(
                                    trustStore,
                                    System.getProperty("e2e.trustStorePassword", "changeit"));
        }
        return RestAssuredConfig.config().sslConfig(sslConfig);
    }

    public static void initRestAssured() {
        String baseUrl = getBaseUrl();
        log.info("Configuring REST-Assured with target Base URL: {}", baseUrl);
        RestAssured.baseURI = baseUrl;
        RestAssured.config = restAssuredConfig();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
}
