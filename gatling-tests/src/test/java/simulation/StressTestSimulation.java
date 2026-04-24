package simulation;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A high-load simulation focused on stress testing the microservices architecture with realistic
 * traffic patterns and gradually increasing load.
 */
public class StressTestSimulation extends BaseSimulation {

    private static final Logger LOGGER = LoggerFactory.getLogger(StressTestSimulation.class);

    // Stress test specific configuration - balanced for sustainable load
    private static final int MAX_USERS = Integer.parseInt(System.getProperty("maxUsers", "50"));
    private static final int PLATEAU_DURATION_MINUTES =
            Integer.parseInt(System.getProperty("plateauDurationMinutes", "5"));

    // Performance SLAs
    private static final int MEAN_RESPONSE_TIME_MS = 1500;
    private static final int P95_RESPONSE_TIME_MS = 3000;
    private static final int P99_RESPONSE_TIME_MS = 5000;
    private static final double MAX_ERROR_PERCENT = 5.0;

    @Override
    public void before() {
        super.before(); // Run health checks
        seedTestData();
    }

    private void seedTestData() {
        try {
            LOGGER.info("Generating catalog test data for stress test scenarios");
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(URI.create(BASE_URL + "/catalog-service/api/catalog/generate"))
                                .GET()
                                .header("Content-Type", "application/json")
                                .build();
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    LOGGER.info("Catalog test data successfully generated.");
                    request =
                            HttpRequest.newBuilder()
                                    .uri(
                                            URI.create(
                                                    BASE_URL
                                                            + "/inventory-service/api/inventory/generate"))
                                    .GET()
                                    .header("Content-Type", "application/json")
                                    .build();
                    response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        LOGGER.info("Inventory test data successfully generated.");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error generating test data: {}", e.getMessage());
        }
    }

    // Assertions configuration
    private io.gatling.javaapi.core.Assertion[] getDefaultAssertions() {
        return new io.gatling.javaapi.core.Assertion[] {
            // Global performance assertions
            global().responseTime().mean().lt(MEAN_RESPONSE_TIME_MS),
            global().responseTime().percentile(95).lt(P95_RESPONSE_TIME_MS),
            global().responseTime().percentile(99).lt(P99_RESPONSE_TIME_MS),
            global().failedRequests().percent().lt(MAX_ERROR_PERCENT),

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
        LOGGER.info("Starting StressTestSimulation with 3-phase injection profile");

        // Initialize main load test scenario
        io.gatling.javaapi.core.ScenarioBuilder mainLoadScenario =
                scenario("Main Load Test")
                        .feed(enhancedProductFeeder())
                        .randomSwitch()
                        .on(
                                new io.gatling.javaapi.core.Choice.WithWeight(
                                        30.0, ScenarioBuilders.browseChain()),
                                new io.gatling.javaapi.core.Choice.WithWeight(
                                        30.0,
                                        ScenarioBuilders.createProductChain()
                                                .exec(ScenarioBuilders.getProductChain())),
                                new io.gatling.javaapi.core.Choice.WithWeight(
                                        20.0, ScenarioBuilders.searchChain()),
                                new io.gatling.javaapi.core.Choice.WithWeight(
                                        20.0,
                                        ScenarioBuilders.createProductChain()
                                                .exec(ScenarioBuilders.updateInventoryChain())
                                                .exec(ScenarioBuilders.createOrderChain())));

        this.setUp(
                        mainLoadScenario.injectOpen(
                                rampUsersPerSec(1)
                                        .to(MAX_USERS / 2.0)
                                        .during(Duration.ofMinutes(2)),
                                constantUsersPerSec(MAX_USERS)
                                        .during(Duration.ofMinutes(PLATEAU_DURATION_MINUTES)),
                                rampUsersPerSec(MAX_USERS).to(1).during(Duration.ofMinutes(1))))
                .protocols(httpProtocol)
                .assertions(getDefaultAssertions());
    }
}
