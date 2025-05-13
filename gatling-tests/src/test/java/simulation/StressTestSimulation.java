package simulation;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.atOnceUsers;
import static io.gatling.javaapi.core.CoreDsl.bodyString;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.jsonPath;
import static io.gatling.javaapi.core.CoreDsl.nothingFor;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.randomSwitch;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Choice;
import io.gatling.javaapi.core.PopulationBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import java.time.Duration;
import java.util.List;
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

    // Flag to enable/disable smoke test phase
    private static final boolean RUN_SMOKE_TEST =
            Boolean.parseBoolean(System.getProperty("runSmokeTest", "true"));

    // Create a reusable chain for viewing catalog
    private final ChainBuilder browseCatalog =
            exec(http("Browse catalog - page 1")
                            .get("/catalog-service/api/catalog?pageNo=0&pageSize=10")
                            .check(status().is(200))
                            .check(jsonPath("$.data[*]").exists()))
                    .pause(Duration.ofMillis(500), Duration.ofSeconds(3))
                    .exec(
                            http("Browse catalog - page 2")
                                    .get("/catalog-service/api/catalog?pageNo=1&pageSize=10")
                                    .check(status().is(200)));

    // Create a chain for product detail view
    private final ChainBuilder viewProductDetail =
            feed(enhancedProductFeeder())
                    .exec(
                            http("View product detail")
                                    .get("/catalog-service/api/catalog/productCode/#{productCode}")
                                    .check(
                                            status().in(200, 404)
                                                    .saveAs("status")) // Allow 404s for random
                                    // product
                                    .check(bodyString().saveAs("productResponse")) // needed later
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

    // Create a chain for order flow - this is more resource intensive
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
                                if (GatlingHelper.isSuccessResponse(session)) {
                                    // Use the extracted price from jsonPath or default
                                    Object extractedPrice = session.get("actualPrice");
                                    double price;
                                    if (extractedPrice != null) {
                                        price = Double.parseDouble(extractedPrice.toString());
                                        LOGGER.debug("Using extracted price: {}", price);
                                    } else {
                                        price = 10.0; // Default value if extraction failed
                                        LOGGER.debug("Using default price: {}", price);
                                    }
                                    return session.set("actualPrice", price);
                                }
                                return session;
                            })
                    .doIf(
                            session ->
                                    session.contains("actualPrice")
                                            && session.get("actualPrice") != null)
                    .then(
                            exec(http("Place order")
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
                                                }
                                                return session;
                                            }));

    // Single smoke test combining all chains to validate functionality with a single user
    private final ChainBuilder smokeTestFlow =
            exec(browseCatalog)
                    .pause(1)
                    .exec(viewProductDetail)
                    .pause(1)
                    .exec(searchProducts)
                    .pause(1)
                    .exec(orderFlow);

    // Create smoke test scenarios - each with a single user to validate all flows work
    private final ScenarioBuilder smokeTest = scenario("Smoke Test").exec(smokeTestFlow);

    // Create different user scenarios for stress testing
    private final ScenarioBuilder casualBrowsers =
            scenario("Casual Browsers")
                    .during(Duration.ofMinutes(RAMP_DURATION_MINUTES + PLATEAU_DURATION_MINUTES))
                    .on(exec(browseCatalog).pause(2, 5).exec(viewProductDetail).pause(1, 3));

    private final ScenarioBuilder activeSearchers =
            scenario("Active Searchers")
                    .during(Duration.ofMinutes(RAMP_DURATION_MINUTES + PLATEAU_DURATION_MINUTES))
                    .on(
                            exec(searchProducts)
                                    .pause(1, 2)
                                    .exec(viewProductDetail)
                                    .pause(1, 3)
                                    .exec(
                                            randomSwitch()
                                                    .on(
                                                            List.of(
                                                                    new Choice.WithWeight(
                                                                            20.0, orderFlow),
                                                                    new Choice.WithWeight(
                                                                            80.0,
                                                                            browseCatalog)))));

    private final ScenarioBuilder powerShoppers =
            scenario("Power Shoppers")
                    .during(Duration.ofMinutes(RAMP_DURATION_MINUTES + PLATEAU_DURATION_MINUTES))
                    .on(
                            exec(browseCatalog)
                                    .pause(0, 1)
                                    .exec(viewProductDetail)
                                    .pause(0, 1)
                                    .exec(orderFlow));

    // Set up the simulation
    {
        runHealthChecks();

        LOGGER.info("Setting up StressTestSimulation with smoke test: {}", RUN_SMOKE_TEST);

        PopulationBuilder smokeTestSetup = null;
        PopulationBuilder stressTestSetup;

        // Single user smoke test to validate all scenarios
        if (RUN_SMOKE_TEST) {
            smokeTestSetup = smokeTest.injectOpen(atOnceUsers(1));

            LOGGER.info(
                    "Smoke test setup completed. Will run with a single user to validate all scenarios.");
        }

        // Main stress test setup
        stressTestSetup =
                casualBrowsers
                        .injectOpen(
                                // Add pause to ensure smoke test finishes first if enabled
                                RUN_SMOKE_TEST
                                        ? nothingFor(Duration.ofSeconds(30))
                                        : nothingFor(Duration.ZERO),
                                rampUsersPerSec(1.0)
                                        .to(MAX_USERS * 0.6)
                                        .during(Duration.ofMinutes(RAMP_DURATION_MINUTES)),
                                constantUsersPerSec(MAX_USERS * 0.6)
                                        .during(Duration.ofMinutes(PLATEAU_DURATION_MINUTES)),
                                rampUsersPerSec(MAX_USERS * 0.6)
                                        .to(0.0)
                                        .during(Duration.ofMinutes(COOL_DOWN_MINUTES)))
                        .andThen(
                                activeSearchers.injectOpen(
                                        rampUsersPerSec(1.0)
                                                .to(MAX_USERS * 0.3)
                                                .during(Duration.ofMinutes(RAMP_DURATION_MINUTES)),
                                        constantUsersPerSec(MAX_USERS * 0.3)
                                                .during(
                                                        Duration.ofMinutes(
                                                                PLATEAU_DURATION_MINUTES)),
                                        rampUsersPerSec(MAX_USERS * 0.3)
                                                .to(0.0)
                                                .during(Duration.ofMinutes(COOL_DOWN_MINUTES))))
                        .andThen(
                                powerShoppers.injectOpen(
                                        nothingFor(
                                                Duration.ofMinutes(
                                                        1)), // delay the power shoppers a bit
                                        rampUsersPerSec(1.0)
                                                .to(MAX_USERS * 0.1)
                                                .during(Duration.ofMinutes(RAMP_DURATION_MINUTES)),
                                        constantUsersPerSec(MAX_USERS * 0.1)
                                                .during(
                                                        Duration.ofMinutes(
                                                                PLATEAU_DURATION_MINUTES)),
                                        rampUsersPerSec(MAX_USERS * 0.1)
                                                .to(0.0)
                                                .during(Duration.ofMinutes(COOL_DOWN_MINUTES))));

        // Set up final simulation with conditional smoke test
        if (RUN_SMOKE_TEST) {
            // Run smoke test first, then stress test only if smoke test passes
            setUp(smokeTestSetup.andThen(stressTestSetup))
                    .protocols(httpProtocol)
                    .assertions(
                            global().responseTime()
                                    .mean()
                                    .lt(1500), // mean response time under 1.5s
                            global().responseTime()
                                    .percentile(95)
                                    .lt(5000), // 95% of responses under 5s
                            global().failedRequests()
                                    .percent()
                                    .lt(5.0) // Less than 5% failed requests
                            );
        } else {
            // Run only stress test
            setUp(stressTestSetup)
                    .protocols(httpProtocol)
                    .assertions(
                            global().responseTime()
                                    .mean()
                                    .lt(1500), // mean response time under 1.5s
                            global().responseTime()
                                    .percentile(95)
                                    .lt(5000), // 95% of responses under 5s
                            global().failedRequests()
                                    .percent()
                                    .lt(5.0) // Less than 5% failed requests
                            );
        }
    }
}
