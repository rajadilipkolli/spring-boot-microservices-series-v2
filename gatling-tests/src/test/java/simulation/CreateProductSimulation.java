package simulation;

import static config.Configuration.ASYNC_POLL_PAUSE_SECONDS;
import static config.Configuration.CONSTANT_USERS;
import static config.Configuration.MAX_PAUSE_MS;
import static config.Configuration.MIN_PAUSE_MS;
import static config.Configuration.RAMP_DURATION_SECONDS;
import static config.Configuration.SLA_MEAN_MS;
import static config.Configuration.SLA_P95_MS;
import static config.Configuration.SLA_P99_MS;
import static config.Configuration.TEST_DURATION_SECONDS;
import static data.Feeders.enhancedProductFeeder;
import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.core.CoreDsl.tryMax;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.Assertion;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performance test simulation for the product creation flow across multiple microservices. This
 * test validates the end-to-end process of creating products, updating inventory, and creating
 * orders.
 */
public class CreateProductSimulation extends BaseLoadSimulation {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateProductSimulation.class);

    // Parameters are now inherited from BaseSimulation for consistency

    private static Duration randomPause() {
        if (MIN_PAUSE_MS <= 0 && MAX_PAUSE_MS <= 0) {
            return Duration.ZERO;
        }
        long min = Math.max(0, MIN_PAUSE_MS);
        long max = Math.max(min + 1, MAX_PAUSE_MS);
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(min, max));
    }

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

    // Pause before the first inventory check so Kafka has time to process the
    // ProductCreated event. Then retry up to 8 times with configured pause between each attempt.
    private final ChainBuilder getInventory =
            exec(session -> session)
                    .pause(Duration.ofSeconds(ASYNC_POLL_PAUSE_SECONDS))
                    .exec(
                            tryMax(8, "inventoryRetryCounter")
                                    .on(
                                            exec(session ->
                                                            session.remove("inventoryId")
                                                                    .remove("availableQuantity")
                                                                    .remove("inventoryStatus"))
                                                    .doIf(
                                                            session ->
                                                                    session.getInt(
                                                                                    "inventoryRetryCounter")
                                                                            > 0)
                                                    .then(
                                                            exec(session -> session)
                                                                    .pause(
                                                                            Duration.ofSeconds(
                                                                                    ASYNC_POLL_PAUSE_SECONDS)))
                                                    .exec(
                                                            http("Get product inventory")
                                                                    .get(
                                                                            "/inventory-service/api/inventory/#{productCode}")
                                                                    .check(
                                                                            status().in(200, 404)
                                                                                    .saveAs(
                                                                                            "inventoryStatus"))
                                                                    .checkIf(
                                                                            session ->
                                                                                    session
                                                                                                    .contains(
                                                                                                            "inventoryStatus")
                                                                                            && session
                                                                                                            .getInt(
                                                                                                                    "inventoryStatus")
                                                                                                    == 200)
                                                                    .then(
                                                                            jsonPath("$.id")
                                                                                    .saveAs(
                                                                                            "inventoryId"),
                                                                            jsonPath(
                                                                                            "$.availableQuantity")
                                                                                    .saveAs(
                                                                                            "availableQuantity")))
                                                    .doIf(
                                                            session ->
                                                                    !session.contains("inventoryId")
                                                                            || session.get(
                                                                                            "inventoryId")
                                                                                    == null)
                                                    .then(
                                                            exec(
                                                                    session -> {
                                                                        LOGGER.warn(
                                                                                "Inventory not yet"
                                                                                        + " available"
                                                                                        + " for product"
                                                                                        + " code: {},"
                                                                                        + " attempt: {},"
                                                                                        + " status: {}",
                                                                                session.getString(
                                                                                        "productCode"),
                                                                                session.getInt(
                                                                                        "inventoryRetryCounter"),
                                                                                session.get(
                                                                                        "inventoryStatus"));
                                                                        return session
                                                                                .markAsFailed();
                                                                    }))))
                    .exec(
                            session -> {
                                if (!session.contains("inventoryId")
                                        || session.get("inventoryId") == null) {
                                    LOGGER.warn(
                                            "Inventory not available after all retries for"
                                                    + " product code: {}",
                                            session.getString("productCode"));
                                    return session.markAsFailed();
                                }
                                int newQuantity = ThreadLocalRandom.current().nextInt(100, 1000);
                                return session.set("newAvailableQuantity", newQuantity);
                            });

    private final ChainBuilder updateInventory =
            exec(http("Update inventory")
                            .put("/inventory-service/api/inventory/product/#{productCode}")
                            .body(
                                    StringBody(
                                            """
    {
      "productCode": "#{productCode}",
      "availableQuantity": #{newAvailableQuantity}
    }
    """))
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
                    .exec(
                            session -> {
                                LOGGER.info(">>> Starting Workflow for user: {}", session.userId());
                                return session;
                            })
                    .exec(createProduct)
                    .exitHereIfFailed()
                    .pause(_ -> randomPause())
                    // Product reads enrich stock availability through inventory-service, so wait
                    // for the asynchronous ProductCreated event before reading the product.
                    .exec(getInventory)
                    .exitHereIfFailed()
                    .exec(getProduct)
                    .pause(_ -> randomPause())
                    .exitHereIfFailed()
                    .exec(updateInventory)
                    .pause(_ -> randomPause())
                    .exec(createOrder)
                    .exec(
                            session -> {
                                LOGGER.info(">>> Finished Workflow for user: {}", session.userId());
                                return session;
                            });

    /** Simulation setup with configurable load profile */
    public CreateProductSimulation() {

        int targetRate = CONSTANT_USERS;
        Duration rampDuration = Duration.ofSeconds(RAMP_DURATION_SECONDS);
        Duration steadyStateDuration = Duration.ofSeconds(TEST_DURATION_SECONDS);

        // Global assertions to validate overall service performance
        this.setUpSimulation(
                rampDuration
                        .plus(steadyStateDuration)
                        .plus(rampDuration)
                        .plus(Duration.ofMinutes(1)),
                new Assertion[] {
                    // Add global performance SLA assertions
                    global().responseTime().mean().lt(SLA_MEAN_MS), // Mean response time
                    global().responseTime().percentile(95).lt(SLA_P95_MS), // 95% of responses
                    global().responseTime().percentile(99).lt(SLA_P99_MS), // 99% of responses
                    global().successfulRequests().percent().gt(95.0), // More than 95% success
                    global().failedRequests().percent().lt(5.0), // Less than 5% failed requests
                    // Request-specific assertions for detailed metrics
                    details("Create product").responseTime().mean().lt(1000),
                    details("Create product").successfulRequests().percent().gt(90.0),
                    details("Create order with product").responseTime().mean().lt(10000),
                    details("Update inventory").responseTime().mean().lt(800)
                },
                productWorkflow.injectOpen(
                        rampUsersPerSec(0).to(targetRate).during(rampDuration),
                        constantUsersPerSec(targetRate).during(steadyStateDuration),
                        rampUsersPerSec(targetRate).to(0).during(rampDuration)));
    }
}
