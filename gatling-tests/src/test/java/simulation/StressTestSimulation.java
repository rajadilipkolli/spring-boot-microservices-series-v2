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
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scenarios.ScenarioBuilders;

/**
 * A high-load simulation focused on stress testing the microservices architecture with realistic
 * traffic patterns and gradually increasing load.
 */
public class StressTestSimulation extends BaseLoadSimulation {

    private static final Logger LOGGER = LoggerFactory.getLogger(StressTestSimulation.class);

    // Stress test specific configuration - balanced for sustainable load
    private static final int MAX_USERS = CONSTANT_USERS;
    private static final int PLATEAU_DURATION_SECONDS = TEST_DURATION_SECONDS;

    // Assertions configuration
    private Assertion[] getDefaultAssertions() {
        return new Assertion[] {
            // Global performance assertions
            global().responseTime().mean().lt(SLA_STRESS_MEAN_MS),
            global().responseTime().percentile(95).lt(SLA_STRESS_P95_MS),
            global().responseTime().percentile(99).lt(SLA_STRESS_P99_MS),
            global().failedRequests().percent().lt(SLA_MAX_ERROR_PERCENT),

            // Order flow assertions
            details("Place order").successfulRequests().percent().gt(SLA_MIN_SUCCESS_PERCENT),
            details("Place order").responseTime().percentile(95).lt(SLA_STRESS_P95_MS),

            // Catalog and search assertions
            details("Browse catalog").successfulRequests().percent().gt(SLA_MIN_SUCCESS_PERCENT),
            details("Browse catalog").responseTime().percentile(95).lt(2000),
            details("Search products").successfulRequests().percent().gt(SLA_MIN_SUCCESS_PERCENT),
            details("Search products").responseTime().percentile(95).lt(2000),

            // Product detail and inventory assertions
            details("Get product detail")
                    .successfulRequests()
                    .percent()
                    .gt(SLA_MIN_SUCCESS_PERCENT),
            details("Get product detail").responseTime().percentile(95).lt(2000),
            details("Update inventory").successfulRequests().percent().gt(SLA_MIN_SUCCESS_PERCENT),
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

        this.setUpSimulation(
                rampDuration.plus(plateauDuration).plus(rampDuration).plus(Duration.ofMinutes(1)),
                getDefaultAssertions(),
                mainLoadScenario.injectOpen(
                        rampUsersPerSec(1).to(MAX_USERS).during(rampDuration),
                        constantUsersPerSec(MAX_USERS).during(plateauDuration),
                        rampUsersPerSec(MAX_USERS).to(1).during(rampDuration)));
    }
}
