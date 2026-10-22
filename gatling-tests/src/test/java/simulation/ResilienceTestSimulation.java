package simulation;

import static config.Configuration.*;
import static data.Feeders.*;
import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.details;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.rampUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.repeat;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.Assertion;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Choice;
import io.gatling.javaapi.core.ScenarioBuilder;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scenarios.ScenarioBuilders;

/**
 * This simulation focuses on testing service resilience and error handling capabilities. It
 * deliberately sends some invalid requests to test error handling.
 */
public class ResilienceTestSimulation extends BaseLoadSimulation {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResilienceTestSimulation.class);

    private static final int TARGET_RATE = CONSTANT_USERS;
    private static final Duration RAMP_DURATION = Duration.ofSeconds(RAMP_DURATION_SECONDS);
    private static final Duration STEADY_STATE_DURATION = Duration.ofSeconds(TEST_DURATION_SECONDS);

    private final AtomicInteger rateLimitedCount = new AtomicInteger(0);
    private final AtomicInteger serviceUnavailableCount = new AtomicInteger(0);

    // Test scenarios
    ChainBuilder getProductConcurrently =
            exec(
                    http("Get product concurrently")
                            .get("/catalog-service/api/catalog/product-code/#{productCode}")
                            .check(status().in(200, 404)));

    ChainBuilder createInvalidProduct =
            exec(
                    http("Create invalid product")
                            .post("/catalog-service/api/catalog")
                            .body(
                                    StringBody(
                                            """
                            {
                              "productCode": "",
                              "productName": "#{productName}",
                              "price": -100.0,
                              "description": "Invalid product test"
                            }
                            """))
                            .asJson()
                            .check(status().in(400, 422)));

    ChainBuilder triggerCircuitBreaker =
            repeat(5)
                    .on(
                            exec(http("Circuit breaker probe")
                                            .get("/catalog-service/api/catalog")
                                            .check(
                                                    status().in(200, 429, 503)
                                                            .saveAs("responseStatus")))
                                    .exec(
                                            session -> {
                                                String statusStr =
                                                        session.getString("responseStatus");
                                                if (statusStr != null) {
                                                    int status = Integer.parseInt(statusStr);
                                                    if (status == 429) {
                                                        rateLimitedCount.incrementAndGet();
                                                    } else if (status == 503) {
                                                        serviceUnavailableCount.incrementAndGet();
                                                    }
                                                }
                                                return session;
                                            }));

    ScenarioBuilder resilienceScenario =
            scenario("Resilience Test Workflow")
                    .feed(validProductFeeder())
                    .randomSwitch()
                    .on(
                            new Choice.WithWeight(40.0, ScenarioBuilders.createProductChain()),
                            new Choice.WithWeight(20.0, createInvalidProduct),
                            new Choice.WithWeight(20.0, getProductConcurrently),
                            new Choice.WithWeight(20.0, triggerCircuitBreaker));

    public ResilienceTestSimulation() {
        LOGGER.info("Starting ResilienceTestSimulation with 3-phase injection profile");

        this.setUpSimulation(
                RAMP_DURATION
                        .plus(STEADY_STATE_DURATION)
                        .plus(RAMP_DURATION)
                        .plus(Duration.ofMinutes(1)),
                new Assertion[] {
                    // P95 latency SLA across all requests
                    global().responseTime().percentile(95).lt(SLA_RESILIENCE_P95_MS),
                    // Valid product creation must succeed: no protocol errors or timeouts
                    details("Create product").failedRequests().percent().is(0.0),
                    // Invalid product creation is expected to be rejected with 4xx — must not
                    // 500
                    details("Create invalid product").failedRequests().percent().is(0.0),
                    // Concurrent read: 200 and 404 are both accepted — this must not timeout
                    details("Get product concurrently").failedRequests().percent().is(0.0),
                    // Circuit breaker probe: 429/503 are accepted — must not produce 500s or
                    // timeouts
                    details("Circuit breaker probe").failedRequests().percent().is(0.0)
                },
                resilienceScenario.injectOpen(
                        rampUsersPerSec(0).to(TARGET_RATE).during(RAMP_DURATION),
                        constantUsersPerSec(TARGET_RATE).during(STEADY_STATE_DURATION),
                        rampUsersPerSec(TARGET_RATE).to(0).during(RAMP_DURATION)));
    }

    @Override
    public void after() {
        LOGGER.info(
                "Resilience Test completed. Rate limited responses: {}, Service unavailable responses: {}",
                rateLimitedCount.get(),
                serviceUnavailableCount.get());
    }
}
