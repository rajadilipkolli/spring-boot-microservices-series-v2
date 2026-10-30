/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.example.e2e.config.E2EConfig;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("e2e")
@DisplayName("E2E - API Gateway Data Generation Orchestration")
class GatewayOrchestrationE2ETest {

    @BeforeAll
    static void setup() {
        E2EConfig.initRestAssured();
    }

    @Test
    @DisplayName("Should orchestrate sample data generation across microservices via Gateway")
    void shouldOrchestrateDataGenerationAcrossServices() {
        String idempotencyKey = UUID.randomUUID().toString();

        given().spec(E2EConfig.defaultRequestSpec())
                .header("Idempotency-Key", idempotencyKey)
                .queryParam("batchSize", 1)
                .when()
                .post("/api/v1/generate")
                .then()
                .statusCode(200)
                .body("status", equalTo("success"))
                .body("message", containsString("Generation process completed successfully"))
                .body("serviceResponses", notNullValue())
                .body("serviceResponses.catalog", notNullValue())
                .body("serviceResponses.inventory", notNullValue())
                .body("serviceResponses.order", notNullValue());
    }

    @ParameterizedTest(name = "Should reject invalid batch size {0}")
    @ValueSource(ints = {0, -1, 10_001})
    @DisplayName("Should reject invalid batch sizes with 400 Bad Request")
    void shouldRejectInvalidBatchSizes(int invalidBatchSize) {
        given().spec(E2EConfig.defaultRequestSpec())
                .queryParam("batchSize", invalidBatchSize)
                .when()
                .post("/api/v1/generate")
                .then()
                .statusCode(400)
                .body("status", equalTo(400))
                .body("title", equalTo("Bad Request"));
    }
}
