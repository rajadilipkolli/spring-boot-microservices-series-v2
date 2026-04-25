package simulation;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Choice;
import io.gatling.javaapi.core.ScenarioBuilder;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This simulation tests API Gateway resilience patterns like circuit breakers and rate limiting. It
 * identifies breaking points in the system by generating targeted load patterns.
 */
public class ApiGatewayResilienceSimulation extends BaseSimulation {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ApiGatewayResilienceSimulation.class);

    private static final int BURST_USERS = CONSTANT_USERS;
    private static final Duration RAMP_DURATION = Duration.ofSeconds(RAMP_DURATION_SECONDS);
    private static final Duration SUSTAIN_DURATION = Duration.ofSeconds(TEST_DURATION_SECONDS);

    // Counter to track rate limiting responses
    private final AtomicInteger rateLimitedCount = new AtomicInteger(0);
    private final AtomicInteger serviceUnavailableCount = new AtomicInteger(0);

    @Override
    public void before() {
        super.before(); // Run health checks
    }

    // Define a rapid-fire request chain to trigger rate limiting
    private final ChainBuilder rapidRequests =
            repeat(5)
                    .on(
                            exec(http("Rapid catalog request")
                                            .get("/catalog-service/api/catalog")
                                            .check(status().saveAs("status")))
                                    .exec(
                                            session -> {
                                                int status =
                                                        Integer.parseInt(
                                                                session.getString("status"));
                                                if (status == 429) {
                                                    rateLimitedCount.incrementAndGet();
                                                } else if (status == 503) {
                                                    serviceUnavailableCount.incrementAndGet();
                                                }
                                                return session;
                                            })
                                    .pause(Duration.ofMillis(100)));

    // Define a chain to test circuit breaker on error responses
    private final ChainBuilder errorTriggeringRequests =
            exec(http("Trigger error path")
                            .get("/catalog-service/api/catalog/error")
                            .check(status().saveAs("errorStatus")))
                    .pause(Duration.ofMillis(200));

    // Define a mixed request pattern using ScenarioBuilders
    private final ChainBuilder mixedRequests =
            randomSwitch()
                    .on(
                            new Choice.WithWeight(30.0, ScenarioBuilders.browseChain()),
                            new Choice.WithWeight(30.0, ScenarioBuilders.getProductChain()),
                            new Choice.WithWeight(20.0, ScenarioBuilders.searchChain()),
                            new Choice.WithWeight(
                                    20.0,
                                    exec(
                                            http("Get inventory")
                                                    .get("/inventory-service/api/inventory/P000001")
                                                    .check(status().is(200)))));

    // Define the scenarios
    private final ScenarioBuilder rateLimitingScenario =
            scenario("API Gateway Rate Limiting Test").exec(rapidRequests);

    private final ScenarioBuilder circuitBreakerScenario =
            scenario("Circuit Breaker Test").exec(errorTriggeringRequests);

    private final ScenarioBuilder mixedLoadScenario =
            scenario("Mixed Gateway Load Test").exec(mixedRequests);

    public ApiGatewayResilienceSimulation() {
        LOGGER.info("Starting ApiGatewayResilienceSimulation with 3-phase injection profile");

        setUp(
                        rateLimitingScenario.injectOpen(
                                rampUsersPerSec(0).to(BURST_USERS / 10.0).during(RAMP_DURATION),
                                constantUsersPerSec(BURST_USERS / 10.0).during(SUSTAIN_DURATION),
                                rampUsersPerSec(BURST_USERS / 10.0).to(0).during(RAMP_DURATION)),
                        circuitBreakerScenario.injectOpen(
                                rampUsersPerSec(0).to(BURST_USERS / 5.0).during(RAMP_DURATION),
                                constantUsersPerSec(BURST_USERS / 5.0).during(SUSTAIN_DURATION),
                                rampUsersPerSec(BURST_USERS / 5.0).to(0).during(RAMP_DURATION)),
                        mixedLoadScenario.injectOpen(
                                rampUsersPerSec(0).to(BURST_USERS / 2.0).during(RAMP_DURATION),
                                constantUsersPerSec(BURST_USERS / 2.0).during(SUSTAIN_DURATION),
                                rampUsersPerSec(BURST_USERS / 2.0).to(0).during(RAMP_DURATION)))
                .protocols(httpProtocol)
                .maxDuration(
                        RAMP_DURATION
                                .plus(SUSTAIN_DURATION)
                                .plus(RAMP_DURATION)
                                .plus(Duration.ofMinutes(1)));
    }

    @Override
    public void after() {
        LOGGER.info(
                "Test completed. Rate limited responses: {}, Service unavailable responses: {}",
                rateLimitedCount.get(),
                serviceUnavailableCount.get());
    }
}
