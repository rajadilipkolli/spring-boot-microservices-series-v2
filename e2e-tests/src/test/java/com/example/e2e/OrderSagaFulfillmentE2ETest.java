package com.example.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import com.example.e2e.config.E2EConfig;
import com.example.e2e.models.Address;
import com.example.e2e.models.CustomerRequest;
import com.example.e2e.models.InventoryRequest;
import com.example.e2e.models.OrderItemRequest;
import com.example.e2e.models.OrderRequest;
import com.example.e2e.models.ProductRequest;
import io.restassured.response.Response;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Tag("e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2E - Distributed Order Fulfillment Saga")
class OrderSagaFulfillmentE2ETest {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaFulfillmentE2ETest.class);

    private static final String TEST_PRODUCT_CODE =
            "E2E-PROD-" + UUID.randomUUID().toString().substring(0, 8);
    private static Long customerId;

    @BeforeAll
    static void setup() {
        E2EConfig.initRestAssured();

        // 1. Ensure product exists in Catalog
        ProductRequest product =
                new ProductRequest(
                        TEST_PRODUCT_CODE,
                        "E2E Test Product",
                        "Product created by E2E test suite",
                        "https://example.com/image.png",
                        new BigDecimal("25.00"));

        log.info("Creating product in catalog: {}", TEST_PRODUCT_CODE);
        given().spec(E2EConfig.defaultRequestSpec())
                .body(product)
                .when()
                .post("/catalog-service/api/catalog")
                .then()
                .statusCode(201);

        // 2. Ensure initial stock in Inventory
        InventoryRequest inventory = new InventoryRequest(TEST_PRODUCT_CODE, 100);
        log.info("Provisioning inventory for product: {}", TEST_PRODUCT_CODE);
        given().spec(E2EConfig.defaultRequestSpec())
                .body(inventory)
                .when()
                .put("/inventory-service/api/inventory/" + TEST_PRODUCT_CODE)
                .then()
                .statusCode(200);

        // 3. Create a test customer with balance in Payment Service
        CustomerRequest customer =
                new CustomerRequest(
                        "E2E Customer " + UUID.randomUUID().toString().substring(0, 6),
                        "e2e-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com",
                        "9876543210",
                        "123 Test Street",
                        "Suite 100",
                        "Hyderabad",
                        "Telangana",
                        "500081",
                        "India",
                        new BigDecimal("1000.00"));

        log.info("Creating customer in payment service");
        Response customerResponse =
                given().spec(E2EConfig.defaultRequestSpec())
                        .body(customer)
                        .when()
                        .post("/payment-service/api/customers")
                        .then()
                        .statusCode(201)
                        .extract()
                        .response();

        customerId = customerResponse.jsonPath().getLong("customerId");
        assertThat(customerId).isNotNull().isPositive();
        log.info("Setup completed. Test Customer ID: {}", customerId);
    }

    @Test
    @Order(1)
    @DisplayName("Happy Path: Order should be CONFIRMED and customer funds deducted")
    void shouldConfirmOrderWhenInventoryAndFundsAreSufficient() {
        Address address =
                new Address("123 Test St", "Apt 4B", "Hyderabad", "Telangana", "500081", "India");
        OrderItemRequest item = new OrderItemRequest(TEST_PRODUCT_CODE, 2, new BigDecimal("25.00"));
        OrderRequest orderRequest = new OrderRequest(customerId, List.of(item), address);

        // Submit order
        Response createOrderResponse =
                given().spec(E2EConfig.defaultRequestSpec())
                        .body(orderRequest)
                        .when()
                        .post("/order-service/api/orders")
                        .then()
                        .statusCode(201)
                        .extract()
                        .response();

        Long orderId = createOrderResponse.jsonPath().getLong("orderId");
        assertThat(orderId).isNotNull().isPositive();
        log.info("Submitted order ID: {}. Awaiting Kafka Streams saga completion...", orderId);

        // Await asynchronous Saga confirmation
        Awaitility.await("Order " + orderId + " should transition to CONFIRMED")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () -> {
                            Response orderCheck =
                                    given().spec(E2EConfig.defaultRequestSpec())
                                            .when()
                                            .get("/order-service/api/orders/" + orderId)
                                            .then()
                                            .statusCode(200)
                                            .extract()
                                            .response();

                            String status = orderCheck.jsonPath().getString("status");
                            log.info("Polled order {} status: {}", orderId, status);
                            assertThat(status).isEqualTo("CONFIRMED");
                        });

        // Verify customer balance was deducted in payment-service (1000.00 - 50.00 = 950.00)
        given().spec(E2EConfig.defaultRequestSpec())
                .when()
                .get("/payment-service/api/customers/" + customerId)
                .then()
                .statusCode(200)
                .body("amountAvailable", equalTo(950.00f));
    }

    @Test
    @Order(2)
    @DisplayName("Rollback Path: Order should REJECT/ROLLBACK when inventory is insufficient")
    void shouldRollbackOrderWhenInsufficientInventory() {
        Address address =
                new Address("123 Test St", "Apt 4B", "Hyderabad", "Telangana", "500081", "India");
        // Request quantity far exceeding stock
        OrderItemRequest item =
                new OrderItemRequest(TEST_PRODUCT_CODE, 5000, new BigDecimal("25.00"));
        OrderRequest orderRequest = new OrderRequest(customerId, List.of(item), address);

        Response createOrderResponse =
                given().spec(E2EConfig.defaultRequestSpec())
                        .body(orderRequest)
                        .when()
                        .post("/order-service/api/orders")
                        .then()
                        .statusCode(201)
                        .extract()
                        .response();

        Long orderId = createOrderResponse.jsonPath().getLong("orderId");
        assertThat(orderId).isNotNull().isPositive();

        // Await Saga rollback
        Awaitility.await("Order " + orderId + " should transition to ROLLBACK or REJECTED")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () -> {
                            Response orderCheck =
                                    given().spec(E2EConfig.defaultRequestSpec())
                                            .when()
                                            .get("/order-service/api/orders/" + orderId)
                                            .then()
                                            .statusCode(200)
                                            .extract()
                                            .response();

                            String status = orderCheck.jsonPath().getString("status");
                            log.info(
                                    "Polled insufficient-inventory order {} status: {}",
                                    orderId,
                                    status);
                            assertThat(status).isIn("ROLLBACK", "REJECTED");
                        });
    }

    @Test
    @Order(3)
    @DisplayName("Rollback Path: Order should REJECT/ROLLBACK when customer funds are insufficient")
    void shouldRollbackOrderWhenInsufficientFunds() {
        // Create a customer with only $10.00
        CustomerRequest lowBalanceCustomer =
                new CustomerRequest(
                        "Low Balance Customer",
                        "lowbalance-" + UUID.randomUUID() + "@example.com",
                        "9876543210",
                        "123 Test Street",
                        null,
                        "Hyderabad",
                        "Telangana",
                        "500081",
                        "India",
                        new BigDecimal("10.00"));

        Response customerResponse =
                given().spec(E2EConfig.defaultRequestSpec())
                        .body(lowBalanceCustomer)
                        .when()
                        .post("/payment-service/api/customers")
                        .then()
                        .statusCode(201)
                        .extract()
                        .response();

        Long poorCustomerId = customerResponse.jsonPath().getLong("customerId");

        Address address =
                new Address("123 Test St", "Apt 4B", "Hyderabad", "Telangana", "500081", "India");
        // Item total: 1 * $25.00 > $10.00
        OrderItemRequest item = new OrderItemRequest(TEST_PRODUCT_CODE, 1, new BigDecimal("25.00"));
        OrderRequest orderRequest = new OrderRequest(poorCustomerId, List.of(item), address);

        Response createOrderResponse =
                given().spec(E2EConfig.defaultRequestSpec())
                        .body(orderRequest)
                        .when()
                        .post("/order-service/api/orders")
                        .then()
                        .statusCode(201)
                        .extract()
                        .response();

        Long orderId = createOrderResponse.jsonPath().getLong("orderId");
        assertThat(orderId).isNotNull().isPositive();

        Awaitility.await("Order " + orderId + " should transition to ROLLBACK or REJECTED")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () -> {
                            Response orderCheck =
                                    given().spec(E2EConfig.defaultRequestSpec())
                                            .when()
                                            .get("/order-service/api/orders/" + orderId)
                                            .then()
                                            .statusCode(200)
                                            .extract()
                                            .response();

                            String status = orderCheck.jsonPath().getString("status");
                            log.info(
                                    "Polled insufficient-funds order {} status: {}",
                                    orderId,
                                    status);
                            assertThat(status).isIn("ROLLBACK", "REJECTED");
                        });
    }
}
