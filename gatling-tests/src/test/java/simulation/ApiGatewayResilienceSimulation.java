package simulation;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Choice;
import io.gatling.javaapi.core.ScenarioBuilder;
import java.time.Duration;
import java.util.List;
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

    // Configuration for gateway resilience tests
    private static final int BURST_USERS = Integer.parseInt(System.getProperty("burstUsers", "50"));
    private static final int SUSTAIN_SECONDS =
            Integer.parseInt(System.getProperty("sustainSeconds", "30"));

    // Counter to track rate limiting responses
    private final AtomicInteger rateLimitedCount = new AtomicInteger(0);
    private final AtomicInteger serviceUnavailableCount = new AtomicInteger(0);

    // Define a rapid-fire request chain to trigger rate limiting
    private final ChainBuilder rapidRequests =
            repeat(10)
                    .on(
                            exec(http("Rapid catalog request")
                                            .get("/catalog-service/api/catalog")
                                            .check(status().saveAs("status")))
                                    .exec(
                                            session -> {
                                                int status =
                                                        Integer.parseInt(
                                                                session.getString("status"));
                                                if (status == 429) { // Too Many Requests
                                                    rateLimitedCount.incrementAndGet();
                                                    LOGGER.info(
                                                            "Rate limit triggered: {}",
                                                            rateLimitedCount.get());
                                                } else if (status == 503) { // Service Unavailable
                                                    serviceUnavailableCount.incrementAndGet();
                                                    LOGGER.info(
                                                            "Circuit breaker triggered: {}",
                                                            serviceUnavailableCount.get());
                                                }
                                                return session;
                                            })
                                    .pause(Duration.ofMillis(100)));

    // Define a chain to test circuit breaker on error responses
    private final ChainBuilder errorTriggeringRequests =
            exec(http("Trigger error path")
                            .get("/catalog-service/api/catalog/error")
                            .check(status().saveAs("errorStatus")))
                    .exec(
                            session -> {
                                int status = Integer.parseInt(session.getString("errorStatus"));
                                LOGGER.debug("Error path returned status: {}", status);
                                return session;
                            })
                    .pause(Duration.ofMillis(200));

    // Define a mixed request pattern
    private final ChainBuilder mixedRequests =
            exec(
                    randomSwitch()
                            .on(
                                    List.of(
                                            new Choice.WithWeight(
                                                    50.0,
                                                    exec(
                                                            http("Get product")
                                                                    .get(
                                                                            "/catalog-service/api/catalog/P000001")
                                                                    .check(
                                                                            status().saveAs(
                                                                                            "productStatus")))),
                                            new Choice.WithWeight(
                                                    50.0,
                                                    exec(
                                                            http("List products")
                                                                    .get(
                                                                            "/catalog-service/api/catalog")
                                                                    .check(
                                                                            status().saveAs(
                                                                                            "catalogStatus")))),
                                            new Choice.WithWeight(
                                                    50.0,
                                                    exec(
                                                            http("Get inventory")
                                                                    .get(
                                                                            "/inventory-service/api/inventory/P000001")
                                                                    .check(
                                                                            status().saveAs(
                                                                                            "inventoryStatus")))),
                                            new Choice.WithWeight(
                                                    50.0,
                                                    exec(
                                                            http("Get orders")
                                                                    .get(
                                                                            "/order-service/api/orders")
                                                                    .check(
                                                                            status().saveAs(
                                                                                            "orderStatus")))))));

    // Define the scenarios
    private final ScenarioBuilder rateLimitingTest =
            scenario("API Gateway Rate Limiting Test")
                    .exec(rapidRequests)
                    .pause(Duration.ofSeconds(2))
                    .exec(rapidRequests);

    private final ScenarioBuilder circuitBreakerTest =
            scenario("Circuit Breaker Test")
                    .exec(errorTriggeringRequests)
                    .pause(Duration.ofSeconds(1))
                    .repeat(5)
                    .on(errorTriggeringRequests);

    private final ScenarioBuilder mixedLoadTest =
            scenario("Mixed Gateway Load Test")
                    .during(Duration.ofSeconds(SUSTAIN_SECONDS))
                    .on(exec(mixedRequests).pause(Duration.ofMillis(200), Duration.ofMillis(500)));

    // Set up the simulation
    public ApiGatewayResilienceSimulation() {
        runHealthChecks();

        setUp(
                        rateLimitingTest.injectOpen(
                                nothingFor(Duration.ofSeconds(2)), atOnceUsers(BURST_USERS / 2)),
                        circuitBreakerTest.injectOpen(
                                nothingFor(Duration.ofSeconds(10)),
                                rampUsers(BURST_USERS / 4).during(Duration.ofSeconds(5))),
                        mixedLoadTest.injectOpen(
                                rampUsers(BURST_USERS).during(Duration.ofSeconds(10)),
                                constantUsersPerSec((double) BURST_USERS / 10)
                                        .during(Duration.ofSeconds(SUSTAIN_SECONDS))))
                .protocols(httpProtocol);
    }

    @Override
    public void after() {
        LOGGER.info(
                "Test completed. Rate limited responses: {}, Service unavailable responses: {}",
                rateLimitedCount.get(),
                serviceUnavailableCount.get());
    }
}
