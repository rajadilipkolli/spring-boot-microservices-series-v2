/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.e2e.config;

import static org.awaitility.Awaitility.await;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.config.SSLConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class E2EConfig {

    private static final Logger log = LoggerFactory.getLogger(E2EConfig.class);

    private static final String DEFAULT_BASE_URL = "http://localhost:8765";
    private static final String DEFAULT_KEYCLOAK_URL = "http://localhost:9191";
    private static final String DEFAULT_WEBAPP_URL = "http://localhost:8080";

    /**
     * Guards the one-time warm-up call so only the first test class that calls {@link
     * #initRestAssured()} pays the cost. All subsequent classes share the already-warmed-up
     * services.
     */
    private static final AtomicBoolean WARMED_UP = new AtomicBoolean(false);

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
        return getCredential(
                "keycloak.password", "KEYCLOAK_RETAIL_PASSWORD", "demo-throwaway-retail-pass");
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

    /**
     * Returns a {@link RequestSpecification} pre-configured with the API Gateway base URL, JSON
     * content type, and the shared {@link RestAssuredConfig}.
     */
    public static RequestSpecification defaultRequestSpec() {
        return new RequestSpecBuilder()
                .setBaseUri(getBaseUrl())
                .setConfig(restAssuredConfig())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();
    }

    /**
     * Returns a {@link RequestSpecification} identical to {@link #defaultRequestSpec()} but with
     * the supplied bearer token attached as an {@code Authorization: Bearer} header. Use this when
     * testing endpoints that require an authenticated caller (e.g. the APIs consumed by
     * retail-store-webapp).
     *
     * @param bearerToken a valid OAuth2 access token
     */
    public static RequestSpecification authenticatedRequestSpec(String bearerToken) {
        return new RequestSpecBuilder()
                .setBaseUri(getBaseUrl())
                .setConfig(restAssuredConfig())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addHeader("Authorization", "Bearer " + bearerToken)
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
        return RestAssuredConfig.config()
                .sslConfig(sslConfig)
                .httpClient(
                        HttpClientConfig.httpClientConfig()
                                .connectTimeout(Duration.ofSeconds(10))
                                .readTimeout(Duration.ofSeconds(30)));
    }

    /**
     * Initialises REST-Assured global state and triggers the one-time service warm-up. Safe to call
     * from multiple {@code @BeforeAll} methods — only the first invocation performs the warm-up;
     * subsequent calls are no-ops for the warm-up step.
     */
    public static void initRestAssured() {
        String baseUrl = getBaseUrl();
        log.info("Configuring REST-Assured with target Base URL: {}", baseUrl);
        RestAssured.baseURI = baseUrl;
        RestAssured.config = restAssuredConfig();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        warmupServicesIfEnabled();
    }

    private static void warmupServicesIfEnabled() {
        boolean warmupEnabled =
                Boolean.parseBoolean(System.getProperty("e2e.warmup.enabled", "true"));
        if (!warmupEnabled || !WARMED_UP.compareAndSet(false, true)) {
            return;
        }

        log.info("Waiting for API Gateway and downstream services to be discovered and UP...");
        List<String> healthEndpoints =
                List.of(
                        "/actuator/health",
                        "/catalog-service/actuator/health",
                        "/inventory-service/actuator/health",
                        "/order-service/actuator/health",
                        "/payment-service/actuator/health");

        for (String endpoint : healthEndpoints) {
            try {
                await("Endpoint " + endpoint + " should be UP via Gateway")
                        .atMost(Duration.ofSeconds(60))
                        .pollInterval(Duration.ofSeconds(2))
                        .until(
                                () -> {
                                    try {
                                        Response r =
                                                RestAssured.given()
                                                        .spec(defaultRequestSpec())
                                                        .when()
                                                        .get(endpoint);
                                        return r.statusCode() == 200
                                                && "UP".equals(r.jsonPath().getString("status"));
                                    } catch (Exception ex) {
                                        return false;
                                    }
                                });
                log.info("Health check passed for: {}", endpoint);
            } catch (Exception ex) {
                log.warn(
                        "Health check for {} did not report UP within timeout: {}",
                        endpoint,
                        ex.getMessage());
            }
        }

        String batchId = UUID.randomUUID().toString();
        log.info(
                "Warming up services via API Gateway /api/v1/generate?batchSize=1 (batchId: {})...",
                batchId);
        try {
            Response response =
                    RestAssured.given()
                            .spec(defaultRequestSpec())
                            .header("Idempotency-Key", batchId)
                            .queryParam("batchSize", 1)
                            .when()
                            .post("/api/v1/generate")
                            .then()
                            .extract()
                            .response();

            if (response.statusCode() == 200) {
                log.info("Warm-up completed successfully: {}", response.asString());
            } else {
                log.warn(
                        "Warm-up call returned status {}: {}. Continuing with test execution.",
                        response.statusCode(),
                        response.asString());
            }
        } catch (Exception ex) {
            log.warn(
                    "Warm-up call to /api/v1/generate encountered an error: {}. Continuing with test"
                            + " execution.",
                    ex.getMessage());
        }
    }
}
