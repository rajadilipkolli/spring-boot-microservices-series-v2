/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.example.e2e.config.E2EConfig;
import io.restassured.response.Response;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("e2e")
@DisplayName("E2E - API Gateway Observability & Tracing")
class GatewayObservabilityE2ETest {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @BeforeAll
    static void setup() {
        E2EConfig.initRestAssured();
    }

    @ParameterizedTest(name = "Gateway should inject X-Trace-Id when routing to {0}")
    @ValueSource(
            strings = {
                "/catalog-service/api/catalog",
                "/inventory-service/api/inventory",
                "/order-service/api/orders",
                "/payment-service/api/customers"
            })
    void shouldInjectTraceIdOnSuccessfulDownstreamRouting(String path) {
        Response response =
                given().spec(E2EConfig.defaultRequestSpec())
                        .when()
                        .get(path)
                        .then()
                        .statusCode(200)
                        .header(TRACE_ID_HEADER, notNullValue())
                        .extract()
                        .response();

        String traceId = response.header(TRACE_ID_HEADER);
        assertThat(traceId).isNotBlank().hasSizeGreaterThanOrEqualTo(16);
    }

    @Test
    @DisplayName("Gateway should propagate X-Trace-Id downstream and expose in error responses")
    void shouldPropagateTraceIdDownstreamAndExposeInErrorResponse() {
        String clientTraceId = UUID.randomUUID().toString().replace("-", "");

        Response response =
                given().spec(E2EConfig.defaultRequestSpec())
                        .header(TRACE_ID_HEADER, clientTraceId)
                        .when()
                        .get("/inventory-service/api/inventory/product")
                        .then()
                        .statusCode(400)
                        .header(TRACE_ID_HEADER, notNullValue())
                        .body("traceId", equalTo(clientTraceId))
                        .extract()
                        .response();

        String traceId = response.header(TRACE_ID_HEADER);
        assertThat(traceId).isEqualTo(clientTraceId);
        assertThat(response.jsonPath().getString("traceId")).isEqualTo(clientTraceId);
    }

    @Test
    @DisplayName("Gateway should inject X-Trace-Id header even on downstream 404 responses")
    void shouldInjectTraceIdOnNotFoundResponses() {
        Response response =
                given().spec(E2EConfig.defaultRequestSpec())
                        .when()
                        .get(
                                "/catalog-service/api/catalog/product-code/NON_EXISTENT_PROD_"
                                        + UUID.randomUUID())
                        .then()
                        .statusCode(404)
                        .header(TRACE_ID_HEADER, notNullValue())
                        .extract()
                        .response();

        String traceId = response.header(TRACE_ID_HEADER);
        assertThat(traceId).isNotBlank();
    }
}
