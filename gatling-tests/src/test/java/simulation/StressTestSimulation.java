package simulation;

import static config.Configuration.*;
import static data.Feeders.*;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.Assertion;
import io.gatling.javaapi.core.Choice;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scenarios.ScenarioBuilders;

/**
 * A high-load simulation focused on stress testing the microservices architecture with realistic
 * traffic patterns and gradually increasing load.
 */
public class StressTestSimulation extends Simulation {

    private static final Logger LOGGER = LoggerFactory.getLogger(StressTestSimulation.class);

    // Stress test specific configuration - balanced for sustainable load
    private static final int MAX_USERS = CONSTANT_USERS;
    private static final int PLATEAU_DURATION_SECONDS = TEST_DURATION_SECONDS;

    // Performance SLAs
    private static final int MEAN_RESPONSE_TIME_MS = 1500;
    private static final int P95_RESPONSE_TIME_MS = 3000;
    private static final int P99_RESPONSE_TIME_MS = 5000;
    private static final double MAX_ERROR_PERCENT = 5.0;

    // Assertions configuration
    private Assertion[] getDefaultAssertions() {
        return new Assertion[] {
            // Global performance assertions
            global().responseTime().percentile(95).lt(MEAN_RESPONSE_TIME_MS),
            global().responseTime().percentile(95).lt(P95_RESPONSE_TIME_MS),
            global().responseTime().percentile(99).lt(P99_RESPONSE_TIME_MS),
            global().failedRequests().percent().lt(MAX_ERROR_PERCENT),

            // Order flow assertions
            details("Place order").successfulRequests().percent().gt(95.0),
            details("Place order").responseTime().percentile(95).lt(3000),

            // Catalog and search assertions
            details("Browse catalog").successfulRequests().percent().gt(95.0),
            details("Browse catalog").responseTime().percentile(95).lt(2000),
            details("Search products").successfulRequests().percent().gt(95.0),
            details("Search products").responseTime().percentile(95).lt(2000),

            // Product detail and inventory assertions
            details("Get product detail").successfulRequests().percent().gt(95.0),
            details("Get product detail").responseTime().percentile(95).lt(2000),
            details("Update inventory").successfulRequests().percent().gt(95.0),
            details("Update inventory").responseTime().percentile(95).lt(2000)
        };
    }

    // Set up the simulation
    public StressTestSimulation() {
        LOGGER.info("Starting StressTestSimulation with 3-phase injection profile");

        // Initialize main load test scenario
        ScenarioBuilder mainLoadScenario =
                scenario("Main Load Test")
                        .feed(enhancedProductFeeder())
                        .randomSwitch()
                        .on(
                                new Choice.WithWeight(30.0, ScenarioBuilders.browseChain()),
                                new Choice.WithWeight(
                                        30.0,
                                        ScenarioBuilders.createProductChain()
                                                .exec(ScenarioBuilders.getProductChain())),
                                new Choice.WithWeight(20.0, ScenarioBuilders.searchChain()),
                                new Choice.WithWeight(
                                        20.0,
                                        ScenarioBuilders.createProductChain()
                                                .exec(ScenarioBuilders.updateInventoryChain())
                                                .exec(ScenarioBuilders.createOrderChain())));

        Duration rampDuration = Duration.ofSeconds(RAMP_DURATION_SECONDS);
        Duration plateauDuration = Duration.ofSeconds(PLATEAU_DURATION_SECONDS);

        this.setUp(
                        mainLoadScenario.injectOpen(
                                rampUsersPerSec(1).to(MAX_USERS / 2.0).during(rampDuration),
                                constantUsersPerSec(MAX_USERS).during(plateauDuration),
                                rampUsersPerSec(MAX_USERS).to(1).during(rampDuration)))
                .protocols(HTTP_PROTOCOL)
                .maxDuration(
                        rampDuration
                                .plus(plateauDuration)
                                .plus(rampDuration)
                                .plus(Duration.ofMinutes(1)))
                .assertions(getDefaultAssertions());
    }
}
