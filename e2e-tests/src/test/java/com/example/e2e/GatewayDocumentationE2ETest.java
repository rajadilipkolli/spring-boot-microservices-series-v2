/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import com.example.e2e.config.E2EConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("e2e")
@DisplayName("E2E - API Gateway OpenAPI & Operational Endpoints")
class GatewayDocumentationE2ETest {

    @BeforeAll
    static void setup() {
        E2EConfig.initRestAssured();
    }

    @Test
    @DisplayName("API Gateway should serve OpenAPI 3.0 specification at /v3/api-docs")
    void gatewayShouldServeOpenApiDocs() {
        given().spec(E2EConfig.defaultRequestSpec())
                .when()
                .get("/v3/api-docs")
                .then()
                .statusCode(200)
                .body("openapi", startsWith("3."))
                .body("paths", notNullValue())
                .body("paths.'/api/v1/generate'", notNullValue())
                .body("paths.'/fallback/api/inventory/{id}'", notNullValue());
    }

    @Test
    @DisplayName("API Gateway should expose Actuator metrics endpoint")
    void gatewayShouldExposeMetrics() {
        given().spec(E2EConfig.defaultRequestSpec())
                .when()
                .get("/actuator/metrics")
                .then()
                .statusCode(200)
                .body("names", notNullValue());
    }

    @Test
    @DisplayName("API Gateway should expose Actuator Prometheus scrape endpoint")
    void gatewayShouldExposePrometheus() {
        given().spec(E2EConfig.defaultRequestSpec())
                .when()
                .get("/actuator/prometheus")
                .then()
                .statusCode(200)
                .body(containsString("# HELP"));
    }
}
