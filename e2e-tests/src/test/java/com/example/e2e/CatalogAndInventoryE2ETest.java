package com.example.e2e;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import com.example.e2e.config.E2EConfig;
import com.example.e2e.models.InventoryRequest;
import com.example.e2e.models.ProductRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("e2e")
@DisplayName("E2E - Catalog & Inventory Inter-Service Communication")
class CatalogAndInventoryE2ETest {

    private static final String PRODUCT_CODE =
            "CAT-INV-" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    static void setup() {
        E2EConfig.initRestAssured();
    }

    @Test
    @DisplayName("Should create product, update inventory, and verify inStock status")
    void shouldCreateProductAndUpdateInventory() {
        // 1. Create product in Catalog Service
        ProductRequest product =
                new ProductRequest(
                        PRODUCT_CODE,
                        "Catalog & Inventory Test",
                        "End to End Test for inter-service communication",
                        "https://example.com/item.png",
                        new BigDecimal("49.99"));

        given().spec(E2EConfig.defaultRequestSpec())
                .body(product)
                .when()
                .post("/catalog-service/api/catalog")
                .then()
                .statusCode(201)
                .body("productCode", is(PRODUCT_CODE));

        // 2. Fetch product by code
        given().spec(E2EConfig.defaultRequestSpec())
                .when()
                .get("/catalog-service/api/catalog/product-code/" + PRODUCT_CODE)
                .then()
                .statusCode(200)
                .body("productCode", is(PRODUCT_CODE))
                .body("productName", is("Catalog & Inventory Test"));

        // 3. Await inventory creation via Kafka, then update inventory in Inventory Service
        await("Inventory should be initialized for product " + PRODUCT_CODE)
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(
                        () ->
                                given().spec(E2EConfig.defaultRequestSpec())
                                        .when()
                                        .get("/inventory-service/api/inventory/" + PRODUCT_CODE)
                                        .then()
                                        .statusCode(200)
                                        .body("productCode", is(PRODUCT_CODE)));

        InventoryRequest inventory = new InventoryRequest(PRODUCT_CODE, 75);
        given().spec(E2EConfig.defaultRequestSpec())
                .body(inventory)
                .when()
                .put("/inventory-service/api/inventory/product/" + PRODUCT_CODE)
                .then()
                .statusCode(200)
                .body("productCode", is(PRODUCT_CODE))
                .body("availableQuantity", equalTo(75));

        // 4. Verify catalog-service fetches inStock from inventory-service
        await("Catalog should reflect inStock status for product " + PRODUCT_CODE)
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(
                        () ->
                                given().spec(E2EConfig.defaultRequestSpec())
                                        .queryParam("fetchInStock", true)
                                        .when()
                                        .get(
                                                "/catalog-service/api/catalog/product-code/"
                                                        + PRODUCT_CODE)
                                        .then()
                                        .statusCode(200)
                                        .body("productCode", is(PRODUCT_CODE))
                                        .body("inStock", is(true)));
    }
}
