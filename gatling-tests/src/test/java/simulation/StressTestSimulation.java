package simulation;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.bodyString;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.randomSwitch;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.Assertion;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Choice;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A high-load simulation focused on stress testing the microservices architecture with realistic
 * traffic patterns and gradually increasing load.
 */
public class StressTestSimulation extends BaseSimulation {

    private static final Logger LOGGER = LoggerFactory.getLogger(StressTestSimulation.class);

    // Stress test specific configuration
    private static final int MAX_USERS = Integer.parseInt(System.getProperty("maxUsers", "100"));
    private static final int RAMP_DURATION_MINUTES =
            Integer.parseInt(System.getProperty("rampDurationMinutes", "5"));
    private static final int PLATEAU_DURATION_MINUTES =
            Integer.parseInt(System.getProperty("plateauDurationMinutes", "10"));
    private static final int COOL_DOWN_MINUTES =
            Integer.parseInt(System.getProperty("coolDownMinutes", "2"));

    // Performance SLAs
    private static final int MEAN_RESPONSE_TIME_MS = 1500;
    private static final int P95_RESPONSE_TIME_MS = 3000;
    private static final int P99_RESPONSE_TIME_MS = 5000;
    private static final double MAX_ERROR_PERCENT = 5.0;

    // Think time configurations for more realistic user behavior
    private static final Duration MIN_THINK_TIME = Duration.ofSeconds(2);
    private static final Duration MAX_THINK_TIME = Duration.ofSeconds(10);
    private static final Duration MIN_PAGE_THINK_TIME = Duration.ofMillis(750);
    private static final Duration MAX_PAGE_THINK_TIME = Duration.ofSeconds(3);

    // Pause duration between smoke test and main load test
    private static final Duration SMOKE_TEST_PAUSE = Duration.ofSeconds(30);

    // Create a reusable chain for viewing catalog with realistic think times and error recovery
    private final ChainBuilder browseCatalog =
            exec(http("Browse catalog - page 1")
                            .get("/catalog-service/api/catalog?pageNo=0&pageSize=10")
                            .check(status().is(200))
                            .check(jsonPath("$.data[*]").exists())
                            .check(jsonPath("$.totalPages").saveAs("totalPages")))
                    .exitHereIfFailed()
                    .pause(MIN_PAGE_THINK_TIME, MAX_PAGE_THINK_TIME)
                    .exec(
                            session -> {
                                // Randomly decide whether to view next page based on total pages
                                int totalPages = session.getInt("totalPages");
                                if (totalPages > 1 && ThreadLocalRandom.current().nextBoolean()) {
                                    return session;
                                }
                                return session.markAsFailed();
                            })
                    .doIf(session -> !session.isFailed())
                    .then(
                            exec(http("Browse catalog - page 2")
                                            .get(
                                                    "/catalog-service/api/catalog?pageNo=1&pageSize=10")
                                            .check(status().is(200)))
                                    .pause(
                                            MIN_PAGE_THINK_TIME,
                                            MAX_PAGE_THINK_TIME)); // Create a chain for product
    // detail view
    private final ChainBuilder viewProductDetail =
            feed(enhancedProductFeeder())
                    .exec(
                            http("View product detail")
                                    .get("/catalog-service/api/catalog/productCode/#{productCode}")
                                    .check(
                                            status().in(200, 404).saveAs("status"),
                                            bodyString().saveAs("productResponse"),
                                            status().is(200)
                                                    .saveAs("foundProduct")) // For smoke test
                            // validation
                            )
                    .exec(
                            session -> {
                                // Get status code by checking if the most recent response is OK
                                if (GatlingHelper.isSuccessResponse(session)) {
                                    return session.set(
                                            "foundProductCode", session.getString("productCode"));
                                }
                                return session;
                            })
                    .doIf(session -> session.contains("foundProductCode"))
                    .then(
                            exec(
                                    http("Check inventory for product")
                                            .get(
                                                    "/inventory-service/api/inventory/#{foundProductCode}")
                                            .check(status().is(200))));

    // Create a chain for search operations
    private final ChainBuilder searchProducts =
            exec(http("Search for product by term")
                            .get("/catalog-service/api/catalog/search?term=product")
                            .check(status().is(200)))
                    .pause(Duration.ofMillis(1000), Duration.ofSeconds(2))
                    .exec(
                            http("Search for product by price range")
                                    .get(
                                            "/catalog-service/api/catalog/search?minPrice=10&maxPrice=100")
                                    .check(status().is(200)));

    // Create a chain for order flow with proper inventory check
    private final ChainBuilder orderFlow =
            feed(enhancedProductFeeder())
                    .exec(
                            http("Check if product exists")
                                    .get("/catalog-service/api/catalog/productCode/#{productCode}")
                                    .check(
                                            status().in(200, 404).saveAs("status"),
                                            jsonPath("$.price").optional().saveAs("actualPrice")))
                    .exec(
                            session -> {
                                // Check if product exists and has price
                                if (session.contains("actualPrice")
                                        && session.get("actualPrice") != null) {
                                    LOGGER.debug(
                                            "Product found with price: {}",
                                            (Object) session.get("actualPrice"));
                                    return session;
                                }
                                LOGGER.debug("Product not found or no price available");
                                return session.markAsFailed();
                            })
                    .exitHereIfFailed()
                    .exec(
                            http("Check product inventory")
                                    .get("/inventory-service/api/inventory/#{productCode}")
                                    .check(status().is(200))
                                    .check(jsonPath("$.quantity").exists()))
                    .exitHereIfFailed()
                    .exec(
                            http("Place order")
                                    .post("/order-service/api/orders")
                                    .body(
                                            StringBody(
                                                    """
                                    {
                                      "customerId": #{customerId},
                                      "shippingAddress": {
                                        "street": "#{street}",
                                        "city": "#{city}",
                                        "zipCode": "#{zipCode}",
                                        "country": "#{country}"
                                      },
                                      "items": [
                                        {
                                          "productCode": "#{productCode}",
                                          "quantity": #{quantity},
                                          "productPrice": #{actualPrice}
                                        }
                                      ]
                                    }
                                    """))
                                    .asJson()
                                    .check(status().is(201))
                                    .check(header("location").saveAs("orderLocation")))
                    .exec(
                            session -> {
                                if (session.contains("orderLocation")) {
                                    LOGGER.debug(
                                            "Order created at: {}",
                                            session.getString("orderLocation"));
                                    return session;
                                }
                                LOGGER.error("Order creation failed - no order location returned");
                                return session.markAsFailed();
                            });

    // Create a smoke test product first
    private final ChainBuilder createSmokeTestProduct =
            feed(enhancedProductFeeder())
                    .exec(
                            http("Create smoke test product")
                                    .post("/catalog-service/api/catalog")
                                    .body(
                                            StringBody(
                                                    """
                                            {
                                              "productCode": "#{productCode}",
                                              "productName": "#{productName}",
                                              "description": "Smoke Test Product",
                                              "price": #{price},
                                              "quantity": #{quantity}
                                            }
                                            """))
                                    .asJson()
                                    .check(status().is(201))
                                    .check(header("location").saveAs("productLocation")))
                    .exec(
                            session -> {
                                LOGGER.info(
                                        "Created smoke test product at: {}",
                                        session.getString("productLocation"));
                                return session;
                            });

    // Add inventory initialization chain
    private final ChainBuilder initializeInventory =
            exec(http("Initialize inventory")
                            .post("/inventory-service/api/inventory")
                            .body(
                                    StringBody(
                                            """
                            {
                              "productCode": "#{productCode}",
                              "quantity": #{quantity}
                            }
                            """))
                            .asJson()
                            .check(status().is(201))
                            .check(header("location").saveAs("inventoryLocation")))
                    .exitHereIfFailed()
                    .exec(
                            session -> {
                                LOGGER.info(
                                        "Initialized inventory for product: {}",
                                        session.getString("productCode"));
                                return session;
                            });

    // Assertions configuration
    private Assertion[] getDefaultAssertions() {
        return new Assertion[] {
            // Global performance assertions
            global().responseTime().mean().lt(MEAN_RESPONSE_TIME_MS),
            global().responseTime().percentile(95).lt(P95_RESPONSE_TIME_MS),
            global().responseTime().percentile(99).lt(P99_RESPONSE_TIME_MS),
            global().failedRequests().percent().lt(MAX_ERROR_PERCENT),

            // Smoke test assertions (stricter thresholds for single user)
            details("Smoke Test").successfulRequests().percent().is(100.0),
            details("Smoke Test").responseTime().percentile(95).lt(1000),

            // Order flow assertions
            details("Place order").successfulRequests().percent().gt(95.0),
            details("Place order").responseTime().percentile(95).lt(3000),

            // Catalog and search assertions
            details("Browse catalog.*").successfulRequests().percent().gt(95.0),
            details("Browse catalog.*").responseTime().percentile(95).lt(2000),
            details("Search for product.*").successfulRequests().percent().gt(95.0),
            details("Search for product.*").responseTime().percentile(95).lt(2000),

            // Product detail and inventory assertions
            details("View product detail").successfulRequests().percent().gt(95.0),
            details("View product detail").responseTime().percentile(95).lt(2000),
            details("Check inventory for product").successfulRequests().percent().gt(95.0),
            details("Check inventory for product").responseTime().percentile(95).lt(2000)
        };
    }

    // Set up the simulation
    public StressTestSimulation() {
        runHealthChecks();

        // Manually execute a request to generate catalog data
        try {
            LOGGER.info("Generating catalog test data for stress test scenarios");

            // Create a synchronous HTTP client (not using Gatling for this initialization step)
            HttpResponse<String> response;
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(URI.create(BASE_URL + "/catalog-service/api/catalog/generate"))
                                .GET()
                                .header("Content-Type", "application/json")
                                .build();

                // Send request synchronously
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            }

            // Log result
            if (response.statusCode() == 200) {
                LOGGER.info(
                        "Catalog test data successfully generated. Status: {}",
                        response.statusCode());
                // Create a synchronous HTTP client (not using Gatling for this initialization step)
                try (HttpClient client = HttpClient.newHttpClient()) {
                    HttpRequest request =
                            HttpRequest.newBuilder()
                                    .uri(
                                            URI.create(
                                                    BASE_URL
                                                            + "/inventory-service/api/inventory/generate"))
                                    .GET()
                                    .header("Content-Type", "application/json")
                                    .build();

                    // Send request synchronously
                    response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        LOGGER.info(
                                "Inventory test data successfully generated. Status: {}",
                                response.statusCode());
                    } else {
                        LOGGER.warn(
                                "Inventory data generation request returned status: {}",
                                response.statusCode());
                    }
                }
            } else {
                LOGGER.warn(
                        "Catalog data generation request returned status: {}",
                        response.statusCode());
            }
        } catch (Exception e) {
            LOGGER.error("Error generating catalog test data: {}", e.getMessage());
        }

        LOGGER.info("Starting simulation with mandatory smoke test followed by main load test");

        // Initialize scenarios
        ScenarioBuilder smokeTestScenario =
                scenario("Smoke Test")
                        .exec(
                                session -> {
                                    LOGGER.info("Starting smoke test with single user...");
                                    return session;
                                })
                        .exec(createSmokeTestProduct)
                        .exitHereIfFailed()
                        .exec(
                                session -> {
                                    if (!session.contains("productLocation")) {
                                        LOGGER.error(
                                                "Smoke Test Failed: Product creation failed - no product location returned");
                                        return session.markAsFailed();
                                    }
                                    LOGGER.info(
                                            "Created product successfully at: {}",
                                            session.getString("productLocation"));
                                    // Store the created product code for later use
                                    String productCode = session.getString("productCode");
                                    LOGGER.info("Created product code: {}", productCode);
                                    return session;
                                })
                        .pause(Duration.ofSeconds(2)) // Wait for product to be available
                        .exec(browseCatalog)
                        .exitHereIfFailed()
                        .exec(
                                session -> {
                                    LOGGER.info("Browse catalog test passed");
                                    return session;
                                })
                        .exec(
                                // Use the created product for product detail view
                                http("View created product detail")
                                        .get(
                                                session ->
                                                        "/catalog-service/api/catalog/productCode/"
                                                                + session.getString("productCode"))
                                        .check(status().is(200))
                                        .check(
                                                jsonPath("$.productCode")
                                                        .is(
                                                                session ->
                                                                        session.getString(
                                                                                "productCode"))))
                        .exitHereIfFailed()
                        .exec(
                                session -> {
                                    LOGGER.info(
                                            "View product detail test passed for product: {}",
                                            session.getString("productCode"));
                                    return session;
                                })
                        .exec(searchProducts)
                        .exitHereIfFailed()
                        .exec(
                                session -> {
                                    LOGGER.info("Search products test passed");
                                    return session;
                                })
                        .exec(
                                // Use the created product for order flow
                                exec(http("Check created product exists")
                                                .get(
                                                        session ->
                                                                "/catalog-service/api/catalog/productCode/"
                                                                        + session.getString(
                                                                                "productCode"))
                                                .check(status().is(200))
                                                .check(jsonPath("$.price").saveAs("actualPrice")))
                                        .exec(
                                                http("Place order for created product")
                                                        .post("/order-service/api/orders")
                                                        .body(
                                                                StringBody(
                                                                        """
                                    {
                                      "customerId": #{customerId},
                                      "shippingAddress": {
                                        "street": "#{street}",
                                        "city": "#{city}",
                                        "zipCode": "#{zipCode}",
                                        "country": "#{country}"
                                      },
                                      "items": [
                                        {
                                          "productCode": "#{productCode}",
                                          "quantity": #{quantity},
                                          "productPrice": #{actualPrice}
                                        }
                                      ]
                                    }
                                    """))
                                                        .asJson()
                                                        .check(status().is(201))
                                                        .check(
                                                                header("location")
                                                                        .saveAs("orderLocation"))))
                        .exitHereIfFailed()
                        .exec(
                                session -> {
                                    if (!session.contains("orderLocation")) {
                                        LOGGER.error(
                                                "Smoke Test Failed: Order placement failed - no order location returned");
                                        return session.markAsFailed();
                                    }
                                    LOGGER.info(
                                            "Order placement test passed. Order created at: {}",
                                            session.getString("orderLocation"));
                                    return session;
                                })
                        .exec(
                                session -> {
                                    if (session.isFailed()) {
                                        LOGGER.error("Smoke test failed - stopping simulation");
                                        System.exit(1); // Force exit if smoke test failed
                                    }
                                    LOGGER.info("All smoke test scenarios passed successfully!");
                                    LOGGER.info(
                                            "Waiting {} seconds before starting main load test",
                                            SMOKE_TEST_PAUSE.toSeconds());
                                    return session;
                                })
                        .pause(SMOKE_TEST_PAUSE);

        // Create smoke test injection profile
        PopulationBuilder smokeTest = smokeTestScenario.injectOpen(atOnceUsers(1));

        // Initialize main load test scenario (only runs if smoke test passes)
        ScenarioBuilder mainLoadScenario =
                scenario("Main Load Test")
                        .exec(
                                session -> {
                                    LOGGER.info("Starting main load test...");
                                    return session;
                                })
                        .during(Duration.ofMinutes(PLATEAU_DURATION_MINUTES))
                        .on(
                                randomSwitch()
                                        .on(
                                                // Browse catalog - no product creation needed
                                                new Choice.WithWeight(30.0, browseCatalog),
                                                // View product - create product first
                                                new Choice.WithWeight(
                                                        30.0,
                                                        exec(createSmokeTestProduct)
                                                                .exitHereIfFailed()
                                                                .exec(initializeInventory)
                                                                .exitHereIfFailed()
                                                                .exec(viewProductDetail)),
                                                // Search - no product creation needed
                                                new Choice.WithWeight(20.0, searchProducts),
                                                // Order flow - create product first
                                                new Choice.WithWeight(
                                                        20.0,
                                                        exec(createSmokeTestProduct)
                                                                .exitHereIfFailed()
                                                                .exec(initializeInventory)
                                                                .exitHereIfFailed()
                                                                .exec(orderFlow))))
                        .pause(MIN_THINK_TIME, MAX_THINK_TIME)
                        .exec(
                                session -> {
                                    LOGGER.info("Main load test completed");
                                    return session;
                                });

        // Create main load test injection profile
        PopulationBuilder mainLoad =
                mainLoadScenario.injectOpen(
                        // Warm-up phase
                        rampUsersPerSec(0.1)
                                .to((double) MAX_USERS / 4)
                                .during(Duration.ofMinutes(2)),
                        rampUsersPerSec((double) MAX_USERS / 4)
                                .to((double) MAX_USERS / 2)
                                .during(Duration.ofMinutes(2)),
                        rampUsersPerSec((double) MAX_USERS / 2)
                                .to(MAX_USERS)
                                .during(Duration.ofMinutes(RAMP_DURATION_MINUTES)),
                        constantUsersPerSec(MAX_USERS)
                                .during(Duration.ofMinutes(PLATEAU_DURATION_MINUTES)),
                        rampUsersPerSec(MAX_USERS)
                                .to(1.0)
                                .during(Duration.ofMinutes(COOL_DOWN_MINUTES)));

        this.setUp(smokeTest.andThen(mainLoad))
                .protocols(httpProtocol)
                .assertions(getDefaultAssertions());
    }
}
