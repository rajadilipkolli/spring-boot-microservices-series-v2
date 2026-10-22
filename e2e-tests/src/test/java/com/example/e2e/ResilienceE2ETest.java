package com.example.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.e2e.config.E2EConfig;
import com.example.e2e.models.ProductRequest;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.util.UUID;
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
    @DisplayName("Catalog delay below the gateway timeout should succeed")
    void catalogDelayBelowGatewayTimeoutShouldSucceed() {
        String productCode = "SLOW-" + UUID.randomUUID();
        ProductRequest product =
                new ProductRequest(
                        productCode,
                        "Slow call test product",
                        "Product for catalog delay verification",
                        "https://example.com/item.png",
                        new BigDecimal("19.99"));

        given().spec(E2EConfig.defaultRequestSpec())
                .body(product)
                .when()
                .post("/catalog-service/api/catalog")
                .then()
                .statusCode(201);

        // Querying an existing product with ?delay=4 triggers delay logic in controller
        Response response =
                given().spec(E2EConfig.defaultRequestSpec())
                        .queryParam("delay", 4)
                        .when()
                        .get("/catalog-service/api/catalog/product-code/" + productCode)
                        .then()
                        .extract()
                        .response();

        // The 4-second delay is below the configured 5-second gateway response timeout.
        assertThat(response.statusCode()).isEqualTo(200);
    }
}
