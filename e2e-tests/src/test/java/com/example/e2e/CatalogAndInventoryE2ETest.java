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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2E - Catalog & Inventory Inter-Service Communication")
class CatalogAndInventoryE2ETest {

    private static String productCode;
    private static String createdProductId;

    @BeforeAll
    static void setup() {
        E2EConfig.initRestAssured();
        productCode = "CAT-INV-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @Order(1)
    @DisplayName("Step 1: Should create product in Catalog Service")
    void shouldCreateProduct() {
        ProductRequest product =
                new ProductRequest(
                        productCode,
                        "Catalog & Inventory Test",
                        "End to End Test for inter-service communication",
                        "https://example.com/item.png",
                        new BigDecimal("49.99"));

        createdProductId =
                given().spec(E2EConfig.defaultRequestSpec())
                        .body(product)
                        .when()
                        .post("/catalog-service/api/catalog")
                        .then()
                        .statusCode(201)
                        .body("productCode", is(productCode))
                        .extract()
                        .path("id");
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Should fetch created product by code")
    void shouldFetchProductByCode() {
        given().spec(E2EConfig.defaultRequestSpec())
                .when()
                .get("/catalog-service/api/catalog/product-code/" + productCode)
                .then()
                .statusCode(200)
                .body("productCode", is(productCode))
                .body("productName", is("Catalog & Inventory Test"));
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Should await inventory creation via Kafka, then update inventory")
    void shouldAwaitAndSupplyInventory() {
        await("Inventory should be initialized for product " + productCode)
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(
                        () ->
                                given().spec(E2EConfig.defaultRequestSpec())
                                        .when()
                                        .get("/inventory-service/api/inventory/" + productCode)
                                        .then()
                                        .statusCode(200)
                                        .body("productCode", is(productCode)));

        InventoryRequest inventory = new InventoryRequest(productCode, 75);
        given().spec(E2EConfig.defaultRequestSpec())
                .body(inventory)
                .when()
                .put("/inventory-service/api/inventory/product/" + productCode)
                .then()
                .statusCode(200)
                .body("productCode", is(productCode))
                .body("availableQuantity", equalTo(75));
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Should verify catalog-service reflects inStock status from inventory")
    void shouldReflectInStockStatusInCatalog() {
        await("Catalog should reflect inStock status for product " + productCode)
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(
                        () ->
                                given().spec(E2EConfig.defaultRequestSpec())
                                        .queryParam("fetchInStock", true)
                                        .when()
                                        .get(
                                                "/catalog-service/api/catalog/product-code/"
                                                        + productCode)
                                        .then()
                                        .statusCode(200)
                                        .body("productCode", is(productCode))
                                        .body("inStock", is(true)));
    }

    @Test
    @Order(5)
    @DisplayName("Step 5: Should delete product in Catalog and verify soft delete in Inventory")
    void shouldDeleteProductAndPropagateSoftDelete() {
        if (createdProductId == null) {
            createdProductId =
                    given().spec(E2EConfig.defaultRequestSpec())
                            .when()
                            .get("/catalog-service/api/catalog/product-code/" + productCode)
                            .then()
                            .statusCode(200)
                            .extract()
                            .path("id");
        }

        given().spec(E2EConfig.defaultRequestSpec())
                .when()
                .delete("/catalog-service/api/catalog/" + createdProductId)
                .then()
                .statusCode(200);

        // Catalog reads should return 404
        given().spec(E2EConfig.defaultRequestSpec())
                .when()
                .get("/catalog-service/api/catalog/product-code/" + productCode)
                .then()
                .statusCode(404);

        // Inventory should soft-delete the item via Kafka event propagation
        await("Inventory should be soft-deleted for product " + productCode)
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(
                        () ->
                                given().spec(E2EConfig.defaultRequestSpec())
                                        .when()
                                        .get("/inventory-service/api/inventory/" + productCode)
                                        .then()
                                        .statusCode(404));
    }
}
