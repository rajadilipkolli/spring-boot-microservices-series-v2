package simulation;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.bodyString;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsers;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performance test simulation for the product creation flow across multiple microservices. This
 * test validates the end-to-end process of creating products, updating inventory, and creating
 * orders.
 */
public class CreateProductSimulation extends BaseSimulation {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateProductSimulation.class);
    // JSON object mapper for serialization/deserialization
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Configuration parameters - can be externalized to properties
    private static final int RAMP_USERS = Integer.parseInt(System.getProperty("rampUsers", "5"));
    private static final int CONSTANT_USERS =
            Integer.parseInt(System.getProperty("constantUsers", "30"));
    private static final int RAMP_DURATION_SECONDS =
            Integer.parseInt(System.getProperty("rampDuration", "30"));
    private static final int TEST_DURATION_SECONDS =
            Integer.parseInt(System.getProperty("testDuration", "60"));

    // Using parent class httpProtocol configuration

    // Using ThreadLocalRandom for better performance in concurrent scenarios
    private final Iterator<Map<String, Object>> feeder =
            Stream.generate(
                            () -> {
                                ThreadLocalRandom random = ThreadLocalRandom.current();
                                Map<String, Object> data = new HashMap<>();
                                data.put(
                                        "productCode",
                                        "P" + String.format("%06d", random.nextInt(10, 100_000)));
                                data.put("productName", "Product-" + random.nextInt(1000, 10000));
                                data.put("price", random.nextInt(5, 500));
                                data.put("customerId", random.nextInt(1, 1000));
                                data.put("quantity", random.nextInt(1, 20));
                                return data;
                            })
                    .iterator();

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
            exec(
                    http("Get product inventory")
                            .get("/inventory-service/api/inventory/#{productCode}")
                            .check(status().is(200))
                            .check(bodyString().saveAs("inventoryResponseBody")));

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
                    .feed(feeder)
                    .exec(createProduct)
                    .exec(getProduct)
                    .exec(getInventory)
                    .exec(updateInventory)
                    .exec(createOrder);

    /**
     * Prepares the inventory update request by generating a new inventory quantity
     *
     * @param inventoryResponseBody The original inventory response
     * @return JSON string with updated inventory
     */
    private String getBodyAsString(String inventoryResponseBody) {
        try {
            InventoryResponseDTO inventoryResponseDTO =
                    OBJECT_MAPPER.readValue(inventoryResponseBody, InventoryResponseDTO.class);
            int newQuantity = ThreadLocalRandom.current().nextInt(100, 1000);

            String body =
                    OBJECT_MAPPER.writeValueAsString(
                            inventoryResponseDTO.withAvailableQuantity(newQuantity));

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Update Inventory Request: {}", body);
            }
            return body;
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to process inventory JSON: {}", e.getMessage());
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
            InventoryResponseDTO dto =
                    OBJECT_MAPPER.readValue(inventoryResponseBody, InventoryResponseDTO.class);
            return dto.id();
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to parse inventory response: {}", e.getMessage());
            throw new RuntimeException("Error extracting inventory ID", e);
        }
    }

    /** Simulation setup with configurable load profile */
    public CreateProductSimulation() {

        runHealthChecks();

        // Global assertions to validate overall service performance
        this.setUp(
                        productWorkflow
                                // Small pause between steps to simulate realistic user behavior
                                .pause(Duration.ofMillis(500))
                                .injectOpen(
                                        // Ramp up users phase for gradual load increase
                                        rampUsers(RAMP_USERS)
                                                .during(Duration.ofSeconds(RAMP_DURATION_SECONDS)),
                                        // Constant load phase to test system stability
                                        constantUsersPerSec(CONSTANT_USERS)
                                                .during(Duration.ofSeconds(TEST_DURATION_SECONDS))))
                .protocols(httpProtocol)
                .assertions(
                        // Add global performance SLA assertions
                        global().responseTime()
                                .percentile3()
                                .lt(1000), // 95% of requests should be under 1s
                        global().successfulRequests()
                                .percent()
                                .gt(95.0) // At least 95% successful requests
                        );
    }
}
