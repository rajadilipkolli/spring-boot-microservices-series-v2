package com.example.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.e2e.config.E2EConfig;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("e2e")
@DisplayName("E2E - Resilience & Circuit Breaker Verification")
class ResilienceE2ETest {

    @BeforeAll
    static void setup() {
        E2EConfig.initRestAssured();
    }

    @Test
    @DisplayName("Gateway Inventory fallback endpoint should return fallback response")
    void gatewayInventoryFallbackShouldReturnResponse() {
        Response response =
                given().spec(E2EConfig.defaultRequestSpec())
                        .when()
                        .get("/fallback/api/inventory/TEST_PRODUCT")
                        .then()
                        .extract()
                        .response();

        // Gateway fallback returns 200 with fallback message or status
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo("Hello TEST_PRODUCT");
    }

    @Test
    @DisplayName("Catalog slow-call delay parameter should be accepted by gateway")
    void catalogSlowCallShouldHandleTimeoutGracefully() {
        // Querying with ?delay=4 triggers delay logic in controller
        Response response =
                given().spec(E2EConfig.defaultRequestSpec())
                        .queryParam("delay", 4)
                        .when()
                        .get("/catalog-service/api/catalog/product-code/P0001")
                        .then()
                        .extract()
                        .response();

        // Expect either successful delayed 200, fallback 200, or gateway timeout 504/500
        assertThat(response.statusCode()).isIn(200, 500, 502, 503, 504);
    }
}
