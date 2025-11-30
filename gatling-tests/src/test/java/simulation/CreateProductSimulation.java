package simulation;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.bodyString;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.nothingFor;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performance test simulation for the product creation flow across multiple microservices. This
 * test validates the end-to-end process of creating products, updating inventory, and creating
 * orders.
 */
public class CreateProductSimulation extends BaseSimulation {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateProductSimulation.class);

    // Configuration parameters - optimized for sustainable throughput
    private static final int RAMP_USERS = Integer.parseInt(System.getProperty("rampUsers", "5"));
    private static final int CONSTANT_USERS =
            Integer.parseInt(System.getProperty("constantUsers", "5"));
    private static final int RAMP_DURATION_SECONDS =
            Integer.parseInt(System.getProperty("rampDuration", "10"));
    private static final int TEST_DURATION_SECONDS =
            Integer.parseInt(System.getProperty("testDuration", "30"));

    // Breaking down the test flow into reusable components for better readability and
    // maintainability
    private final ChainBuilder createProduct =
            exec(http("Create product")
                    .post("/catalog-service/api/catalog")
                    .body(
                            StringBody(
                                    """
    {
      "productCode": "#{productCode}",
      "productName": "#{productName}",
      "price": #{price},
      "description": "Performance test product"
    }
    """))
                    .asJson()
                    .check(status().is(201))
                    .check(header("location").saveAs("productLocation")))
                    .exec(
                            session -> {
                                LOGGER.debug(
                                        "Created product at: {}",
                                        session.getString("productLocation"));
                                return session;
                            });

    private final ChainBuilder getProduct =
            exec(
                    http("Get created product")
                            .get(
                                    session ->
                                            "/catalog-service"
                                                    + session.getString("productLocation"))
                            .check(status().is(200))
                            .check(
                                    jsonPath("$.productCode")
                                            .is(session -> session.getString("productCode"))));

    private final ChainBuilder getInventory =
            exec(http("Get product inventory")
                    .get("/inventory-service/api/inventory/#{productCode}")
                    .check(status().is(200))
                    .check(bodyString().saveAs("inventoryResponseBody")))
                    .pause(1000) // Add a pause to ensure the response is processed
                    .exec(
                            session -> {
                                // Validate the response body
                                String responseBody = session.getString("inventoryResponseBody");
                                if (responseBody == null || responseBody.trim().isEmpty()) {
                                    LOGGER.warn(
                                            "Empty inventory response detected for product code: {}",
                                            session.getString("productCode"));
                                    return session.markAsFailed();
                                }

                                try {
                                    // Additional validation - try to parse it as JSON
                                    OBJECT_MAPPER.readTree(responseBody);
                                    LOGGER.debug("Got valid inventory response");
                                    return session;
                                } catch (Exception e) {
                                    LOGGER.warn(
                                            "Invalid JSON response for product code: {}, Error: {}",
                                            session.getString("productCode"),
                                            e.getMessage());
                                    return session.markAsFailed();
                                }
                            });

    private final ChainBuilder updateInventory =
            exec(http("Update inventory")
                    .put(
                            session ->
                                    "/inventory-service/api/inventory/"
                                            + getInventoryId(
                                            session.getString(
                                                    "inventoryResponseBody")))
                    .body(
                            StringBody(
                                    session ->
                                            getBodyAsString(
                                                    session.getString(
                                                            "inventoryResponseBody"))))
                    .asJson()
                    .check(status().is(200)))
                    .exec(
                            session -> {
                                LOGGER.debug(
                                        "Updated inventory for product: {}",
                                        session.getString("productCode"));
                                return session;
                            });

    private final ChainBuilder createOrder =
            exec(http("Create order with product")
                    .post("/order-service/api/orders")
                    .body(
                            StringBody(
                                    """
    {
      "customerId": #{customerId},
      "items": [
        {
          "productCode": "#{productCode}",
          "quantity": #{quantity},
          "productPrice": #{price}
        }
      ],
      "deliveryAddress": {
        "addressLine1": "123 Performance Test St",
        "addressLine2": "Suite 456",
        "city": "Test City",
        "state": "TS",
        "zipCode": "12345",
        "country": "Test Country"
      }
    }
    """))
                    .asJson()
                    .check(status().is(201))
                    .check(header("location").saveAs("orderLocation")))
                    .exec(
                            session -> {
                                LOGGER.debug(
                                        "Created order at: {}", session.getString("orderLocation"));
                                return session;
                            });

    // Main scenario combining all steps
    private final ScenarioBuilder productWorkflow =
            scenario("E2E Product Creation Workflow")
                    .feed(enhancedProductFeeder())
                    .exec(createProduct)
                    .pause(Duration.ofMillis(100)) // Small pause for realistic behavior
                    .exec(getProduct)
                    .pause(Duration.ofMillis(100)) // Small pause for realistic behavior
                    .exec(getInventory)
                    .pause(Duration.ofMillis(200)) // Small pause before the critical update
                    .exec(
                            session -> {
                                // Add safeguard to skip inventory update if inventory info is
                                // missing or invalid
                                if (session.contains("inventoryResponseBody")
                                        && session.getString("inventoryResponseBody") != null
                                        && !Objects.requireNonNull(
                                                session.getString("inventoryResponseBody"))
                                        .trim()
                                        .isEmpty()) {
                                    return session;
                                } else {
                                    LOGGER.warn(
                                            "Skipping inventory update due to missing inventory data");
                                    // Return marked as failed so we don't attempt to create an
                                    // order based on invalid inventory
                                    return session.markAsFailed();
                                }
                            })
                    .exec(updateInventory)
                    .pause(Duration.ofMillis(100)) // Small pause for realistic behavior
                    .exec(createOrder);

    /**
     * Prepares the inventory update request by generating a new inventory quantity
     *
     * @param inventoryResponseBody The original inventory response
     * @return JSON string with updated inventory
     */
    private String getBodyAsString(String inventoryResponseBody) {
        if (inventoryResponseBody == null || inventoryResponseBody.trim().isEmpty()) {
            LOGGER.error("Empty inventory response body");
            throw new RuntimeException("Empty inventory response body");
        }

        try {
            // Log the raw response to help with debugging
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Processing inventory response: {}", inventoryResponseBody);
            }

            // Add additional validation before parsing
            if (inventoryResponseBody.equals("{}") || inventoryResponseBody.equals("[]")) {
                LOGGER.error("Invalid empty JSON object or array: {}", inventoryResponseBody);
                throw new RuntimeException("Empty JSON structure in inventory response");
            }

            try {
                // First validate that it's valid JSON before trying to deserialize
                OBJECT_MAPPER.readTree(inventoryResponseBody);
            } catch (Exception e) {
                LOGGER.error("Invalid JSON format: {}", e.getMessage());
                throw new RuntimeException("Invalid JSON format in inventory response", e);
            }

            InventoryResponseDTO inventoryResponseDTO =
                    OBJECT_MAPPER.readValue(inventoryResponseBody, InventoryResponseDTO.class);

            if (inventoryResponseDTO == null || inventoryResponseDTO.id() == null) {
                LOGGER.error("Invalid inventory data after deserialization");
                throw new RuntimeException("Invalid inventory data after deserialization");
            }

            int newQuantity = ThreadLocalRandom.current().nextInt(100, 1000);

            String body =
                    OBJECT_MAPPER.writeValueAsString(
                            inventoryResponseDTO.withAvailableQuantity(newQuantity));

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Update Inventory Request: {}", body);
            }
            return body;
        } catch (JsonProcessingException e) {
            LOGGER.error(
                    "Failed to process inventory JSON: {}. Response: {}",
                    e.getMessage(),
                    inventoryResponseBody);
            throw new RuntimeException("Error processing inventory data", e);
        }
    }

    /**
     * Extracts the inventory ID from the inventory response
     *
     * @param inventoryResponseBody The inventory response JSON
     * @return The inventory ID
     */
    private Long getInventoryId(String inventoryResponseBody) {

        try {
            validateInventoryResponse(
                    inventoryResponseBody, "Extracting ID from inventory response");

            InventoryResponseDTO dto =
                    OBJECT_MAPPER.readValue(inventoryResponseBody, InventoryResponseDTO.class);

            if (dto == null) {
                LOGGER.error("Inventory DTO is null after parsing");
                throw new RuntimeException("Inventory DTO is null after parsing");
            }

            if (dto.id() == null) {
                LOGGER.error("Inventory ID is null. Response: {}", inventoryResponseBody);
                throw new RuntimeException("Inventory ID is null");
            }

            return dto.id();
        } catch (JsonProcessingException e) {
            LOGGER.error(
                    "Failed to parse inventory response: {}. Response: {}",
                    e.getMessage(),
                    inventoryResponseBody);
            throw new RuntimeException("Error extracting inventory ID", e);
        }
    }

    private void validateInventoryResponse(String inventoryResponseBody, String operation) {
        if (inventoryResponseBody == null || inventoryResponseBody.trim().isEmpty()) {
            LOGGER.error("Empty inventory response body during {}", operation);
            throw new IllegalArgumentException("Empty inventory response body");
        }

        // Log the raw response to help with debugging
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("{}: {}", operation, inventoryResponseBody);
        }

        // Add additional validation before parsing
        if (inventoryResponseBody.equals("{}") || inventoryResponseBody.equals("[]")) {
            LOGGER.error(
                    "Invalid empty JSON object or array during {}: {}",
                    operation,
                    inventoryResponseBody);
            throw new IllegalArgumentException("Empty JSON structure in inventory response");
        }
    }

    /** Simulation setup with configurable load profile */
    public CreateProductSimulation() {

        runHealthChecks();

        LOGGER.info(
                "Running with warm-up phase of {} seconds with a single user to initialize Kafka",
                KAFKA_INIT_DELAY_SECONDS);

        // Global assertions to validate overall service performance
        this.setUp(
                        productWorkflow.injectOpen(
                                // Initial single user for Kafka initialization
                                atOnceUsers(1),
                                // Wait for Kafka initialization to complete
                                nothingFor(Duration.ofSeconds(KAFKA_INIT_DELAY_SECONDS)),
                                // Ramp up users phase for gradual load increase
                                rampUsers(RAMP_USERS)
                                        .during(Duration.ofSeconds(RAMP_DURATION_SECONDS)),
                                // Constant user arrival rate (not constant concurrent users)
                                constantUsersPerSec(CONSTANT_USERS)
                                        .during(Duration.ofSeconds(TEST_DURATION_SECONDS))))
                .protocols(httpProtocol)
                .assertions(
                        // Add global performance SLA assertions
                        global().responseTime().mean().lt(1500), // Mean response time under 1.5s
                        global().responseTime()
                                .percentile(95)
                                .lt(5000), // 95% of responses under 5s
                        global().responseTime()
                                .percentile(99)
                                .lt(8000), // 99% of responses under 8s
                        global().successfulRequests().percent().gt(95.0), // More than 95% success
                        global().failedRequests().percent().lt(5.0), // Less than 5% failed requests
                        // Request-specific assertions for detailed metrics
                        details("Create product").responseTime().mean().lt(500),
                        details("Create product").successfulRequests().percent().gt(95.0),
                        details("Create order with product").responseTime().mean().lt(800),
                        details("Update inventory").responseTime().mean().lt(400));
    }
}
