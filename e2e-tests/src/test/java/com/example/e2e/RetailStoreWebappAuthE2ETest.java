/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.example.e2e.auth.KeycloakAuthClient;
import com.example.e2e.config.E2EConfig;
import com.example.e2e.models.Address;
import com.example.e2e.models.CustomerRequest;
import com.example.e2e.models.OrderItemRequest;
import com.example.e2e.models.OrderRequest;
import com.example.e2e.models.ProductRequest;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * End-to-End test suite verifying that the APIs consumed by {@code retail-store-webapp} are
 * accessible and properly function when invoked using an OAuth2 Bearer token obtained from Keycloak
 * for the {@code retailstore-webapp} client.
 *
 * <p>Validates the full chain: Keycloak authentication $\to$ Bearer token header $\to$ API Gateway
 * routing $\to$ Downstream microservice (Catalog, Inventory, Customer/Payment, Order).
 */
@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2E - Retail Store WebApp APIs & Authentication")
class RetailStoreWebappAuthE2ETest {

    private static final Logger log = LoggerFactory.getLogger(RetailStoreWebappAuthE2ETest.class);

    private static String bearerToken;
    private static String testProductCode;
    private static String testCustomerEmail;
    private static String testCustomerName;
    private static Long createdCustomerId;
    private static Long createdOrderId;

    @BeforeAll
    static void setup() {
        E2EConfig.initRestAssured();

        bearerToken = KeycloakAuthClient.getAccessToken();
        assertThat(bearerToken)
                .as("Must acquire a valid Keycloak access token for retailstore-webapp client")
                .isNotBlank();
        log.info("Acquired OAuth2 Bearer token for retailstore-webapp client");

        testProductCode = "WEBAPP-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @Order(1)
    @DisplayName("CatalogServiceClient: GET /catalog-service/api/catalog (paginated products)")
    void shouldFetchCatalogProductsWithAuthToken() {
        given().spec(E2EConfig.authenticatedRequestSpec(bearerToken))
                .queryParam("pageNo", 0)
                .when()
                .get("/catalog-service/api/catalog")
                .then()
                .statusCode(200)
                .body("data", notNullValue())
                .body("pageNumber", notNullValue());
    }

    @Test
    @Order(2)
    @DisplayName("CatalogServiceClient: POST /catalog-service/api/catalog (create product)")
    void shouldCreateProductWithAuthToken() {
        ProductRequest product =
                new ProductRequest(
                        testProductCode,
                        "Webapp E2E Product",
                        "Created by RetailStoreWebappAuthE2ETest",
                        "https://example.com/item.png",
                        new BigDecimal("29.99"));

        Response response =
                given().spec(E2EConfig.authenticatedRequestSpec(bearerToken))
                        .body(product)
                        .when()
                        .post("/catalog-service/api/catalog")
                        .then()
                        .statusCode(201)
                        .body("productCode", is(testProductCode))
                        .extract()
                        .response();

        log.info("Created catalog product {} with ID {}", testProductCode, response.path("id"));
    }

    @Test
    @Order(3)
    @DisplayName(
            "InventoryServiceClient: GET /inventory-service/api/inventory (paginated inventory)")
    void shouldFetchInventoriesWithAuthToken() {
        await("Inventory items should be fetchable via Gateway")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () ->
                                given().spec(E2EConfig.authenticatedRequestSpec(bearerToken))
                                        .queryParam("pageNo", 0)
                                        .when()
                                        .get("/inventory-service/api/inventory")
                                        .then()
                                        .statusCode(200)
                                        .body("data", notNullValue())
                                        .body("totalElements", greaterThanOrEqualTo(0)));
    }

    @Test
    @Order(4)
    @DisplayName("CustomerServiceClient: POST /payment-service/api/customers (getOrCreateCustomer)")
    void shouldCreateOrGetCustomerWithAuthToken() {
        testCustomerEmail =
                "webapp-auth-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        testCustomerName = "Webapp User " + UUID.randomUUID().toString().substring(0, 8);
        CustomerRequest customer =
                new CustomerRequest(
                        testCustomerName,
                        testCustomerEmail,
                        "1234567890",
                        "123 App St",
                        "Suite 1",
                        "Hyderabad",
                        "Telangana",
                        "500081",
                        "India",
                        new BigDecimal("500.00"));

        Response response =
                given().spec(E2EConfig.authenticatedRequestSpec(bearerToken))
                        .body(customer)
                        .when()
                        .post("/payment-service/api/customers")
                        .then()
                        .statusCode(201)
                        .body("customerId", notNullValue())
                        .body("email", is(testCustomerEmail))
                        .extract()
                        .response();

        createdCustomerId = response.jsonPath().getLong("customerId");
        assertThat(createdCustomerId).isNotNull().isPositive();
        log.info("Created test customer with ID: {}", createdCustomerId);
    }

    @Test
    @Order(5)
    @DisplayName("CustomerServiceClient: GET /payment-service/api/customers/{id} (lookup by ID)")
    void shouldGetCustomerByIdWithAuthToken() {
        assertThat(createdCustomerId).isNotNull();

        given().spec(E2EConfig.authenticatedRequestSpec(bearerToken))
                .when()
                .get("/payment-service/api/customers/" + createdCustomerId)
                .then()
                .statusCode(200)
                .body("customerId", is(createdCustomerId.intValue()));
    }

    @Test
    @Order(6)
    @DisplayName(
            "CustomerServiceClient: GET /payment-service/api/customers/by-email (lookup by email)")
    void shouldGetCustomerByEmailWithAuthToken() {
        assertThat(testCustomerEmail).isNotNull();

        given().spec(E2EConfig.authenticatedRequestSpec(bearerToken))
                .queryParam("email", testCustomerEmail)
                .when()
                .get("/payment-service/api/customers/by-email")
                .then()
                .statusCode(200)
                .body("customerId", is(createdCustomerId.intValue()))
                .body("email", is(testCustomerEmail));
    }

    @Test
    @Order(7)
    @DisplayName(
            "CustomerServiceClient: GET /payment-service/api/customers/name/{name} (lookup by name)")
    void shouldGetCustomerByNameWithAuthToken() {
        assertThat(testCustomerName).isNotNull();

        given().spec(E2EConfig.authenticatedRequestSpec(bearerToken))
                .when()
                .get("/payment-service/api/customers/name/" + testCustomerName)
                .then()
                .statusCode(200)
                .body("customerId", is(createdCustomerId.intValue()))
                .body("name", is(testCustomerName));
    }

    @Test
    @Order(8)
    @DisplayName("OrderServiceClient: POST /order-service/api/orders (create order)")
    void shouldCreateOrderWithAuthToken() {
        assertThat(createdCustomerId).isNotNull();

        Address address =
                new Address("123 App St", "Suite 1", "Hyderabad", "Telangana", "500081", "India");
        OrderItemRequest item = new OrderItemRequest(testProductCode, 1, new BigDecimal("29.99"));
        OrderRequest orderRequest = new OrderRequest(createdCustomerId, List.of(item), address);

        Response response =
                given().spec(E2EConfig.authenticatedRequestSpec(bearerToken))
                        .body(orderRequest)
                        .when()
                        .post("/order-service/api/orders")
                        .then()
                        .statusCode(201)
                        .body("orderId", notNullValue())
                        .extract()
                        .response();

        createdOrderId = response.jsonPath().getLong("orderId");
        assertThat(createdOrderId).isNotNull().isPositive();
        log.info("Created order with ID: {}", createdOrderId);
    }

    @Test
    @Order(9)
    @DisplayName("OrderServiceClient: GET /order-service/api/orders (paged orders)")
    void shouldGetOrdersWithAuthToken() {
        given().spec(E2EConfig.authenticatedRequestSpec(bearerToken))
                .when()
                .get("/order-service/api/orders")
                .then()
                .statusCode(200)
                .body("data", notNullValue());
    }

    @Test
    @Order(10)
    @DisplayName("OrderServiceClient: GET /order-service/api/orders/{id} (lookup by ID)")
    void shouldGetOrderByIdWithAuthToken() {
        assertThat(createdOrderId).isNotNull();

        given().spec(E2EConfig.authenticatedRequestSpec(bearerToken))
                .when()
                .get("/order-service/api/orders/" + createdOrderId)
                .then()
                .statusCode(200)
                .body("orderId", is(createdOrderId.intValue()))
                .body("customerId", is(createdCustomerId.intValue()));
    }

    @Test
    @Order(11)
    @DisplayName("OrderServiceClient: GET /order-service/api/orders/customer/{customerId}")
    void shouldGetOrdersByCustomerWithAuthToken() {
        assertThat(createdCustomerId).isNotNull();

        given().spec(E2EConfig.authenticatedRequestSpec(bearerToken))
                .when()
                .get("/order-service/api/orders/customer/" + createdCustomerId)
                .then()
                .statusCode(200)
                .body("data", notNullValue());
    }

    @Test
    @Order(12)
    @DisplayName(
            "CustomerServiceClient: GET /payment-service/api/customers/{id}/orders (cross-service order lookup)")
    void shouldGetOrdersViaPaymentServiceCustomerEndpointWithAuthToken() {
        assertThat(createdCustomerId).isNotNull();

        given().spec(E2EConfig.authenticatedRequestSpec(bearerToken))
                .when()
                .get("/payment-service/api/customers/" + createdCustomerId + "/orders")
                .then()
                .statusCode(200)
                .body("data", notNullValue());
    }
}
