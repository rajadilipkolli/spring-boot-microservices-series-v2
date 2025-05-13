package simulation;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This simulation focuses on testing service resilience and error handling capabilities. It
 * deliberately sends some invalid requests to test error handling.
 */
public class ResilienceTestSimulation extends BaseSimulation {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResilienceTestSimulation.class);

    // Configuration
    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8765");
    private static final int USERS = Integer.parseInt(System.getProperty("users", "20"));
    private static final int TEST_DURATION_SECONDS =
            Integer.parseInt(System.getProperty("testDuration", "120"));

    private final HttpProtocolBuilder httpProtocol =
            http.baseUrl(BASE_URL)
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json")
                    .userAgentHeader("Gatling/Resilience Test")
                    .shareConnections();

    // Valid data feeder
    private final Iterator<Map<String, Object>> validDataFeeder =
            Stream.generate(
                            () -> {
                                ThreadLocalRandom random = ThreadLocalRandom.current();
                                Map<String, Object> data = new HashMap<>();
                                data.put(
                                        "productCode",
                                        "P" + String.format("%06d", random.nextInt(1, 1000)));
                                data.put("productName", "Product-" + random.nextInt(1, 1000));
                                data.put("price", random.nextDouble(10, 1000));
                                data.put("quantity", random.nextInt(1, 100));
                                return data;
                            })
                    .iterator();

    // Invalid data feeder
    private final Iterator<Map<String, Object>> invalidDataFeeder =
            Stream.generate(
                            () -> {
                                Map<String, Object> data = new HashMap<>();
                                // Generate data that will cause validation errors
                                data.put("productCode", "");
                                // Empty product code
                                data.put(
                                        "productName",
                                        ThreadLocalRandom.current().nextBoolean()
                                                ? ""
                                                : "x".repeat(300));
                                // Either empty or too long
                                data.put(
                                        "price",
                                        ThreadLocalRandom.current().nextBoolean()
                                                ? -50.0
                                                : 0.0); // Negative or zero price
                                data.put("quantity", -10); // Negative quantity
                                return data;
                            })
                    .iterator();

    // Test scenarios

    // 1. Scenario for testing high volume of parallel requests to the same product
    ChainBuilder getProductConcurrently =
            exec(
                    http("Get product concurrently")
                            .get("/catalog-service/api/catalog/P000001")
                            .check(status().in(200, 404)));

    // 2. Test service resilience with invalid requests
    ChainBuilder createInvalidProduct =
            exec(
                    http("Create invalid product")
                            .post("/catalog-service/api/catalog")
                            .body(
                                    StringBody(
                                            """
                            {
                              "productCode": "#{productCode}",
                              "productName": "#{productName}",
                              "price": #{price},
                              "description": "Invalid product test"
                            }
                            """))
                            .check(status().in(400, 422))); // Expecting validation error

    // 3. Test throughput with mixed valid and invalid requests
    ChainBuilder createValidProduct =
            exec(
                    http("Create valid product")
                            .post("/catalog-service/api/catalog")
                            .body(
                                    StringBody(
                                            """
                            {
                              "productCode": "#{productCode}",
                              "productName": "#{productName}",
                              "price": #{price},
                              "description": "Valid product test"
                            }
                            """))
                            .check(status().is(201)));

    // 4. Test service circuit breakers
    ChainBuilder triggerCircuitBreaker =
            repeat(10)
                    .on(
                            exec(http("Rapid requests to trigger circuit breaker")
                                            .get("/catalog-service/api/catalog")
                                            .check(status().saveAs("responseStatus")))
                                    .exec(
                                            session -> {
                                                int status =
                                                        Integer.parseInt(
                                                                session.getString(
                                                                        "responseStatus"));
                                                LOGGER.debug(
                                                        "Circuit breaker test status: {}", status);
                                                return session;
                                            }));

    // Combine into scenarios
    ScenarioBuilder validRequestsScenario =
            scenario("Valid Requests").feed(validDataFeeder).exec(createValidProduct);

    ScenarioBuilder invalidRequestsScenario =
            scenario("Invalid Requests").feed(invalidDataFeeder).exec(createInvalidProduct);

    ScenarioBuilder highConcurrencyScenario =
            scenario("High Concurrency").exec(getProductConcurrently);

    ScenarioBuilder circuitBreakerScenario =
            scenario("Circuit Breaker Test").exec(triggerCircuitBreaker);

    // Setup the simulation with multiple scenarios
    public ResilienceTestSimulation() {
        setUp(
                        validRequestsScenario.injectOpen(
                                constantUsersPerSec(5)
                                        .during(Duration.ofSeconds(TEST_DURATION_SECONDS))),
                        invalidRequestsScenario.injectOpen(
                                constantUsersPerSec(2)
                                        .during(Duration.ofSeconds(TEST_DURATION_SECONDS))),
                        highConcurrencyScenario.injectOpen(
                                rampUsersPerSec(1.0)
                                        .to(10.0)
                                        .during(
                                                Duration.ofSeconds(
                                                        Math.min(TEST_DURATION_SECONDS, 30))),
                                constantUsersPerSec(10)
                                        .during(
                                                Duration.ofSeconds(
                                                        Math.max(0, TEST_DURATION_SECONDS - 30)))),
                        circuitBreakerScenario.injectOpen(
                                nothingFor(
                                        Duration.ofSeconds(
                                                60)), // Start this scenario after 1 minute
                                atOnceUsers(USERS / 5)))
                .protocols(httpProtocol)
                .assertions(
                        global().responseTime()
                                .percentile3()
                                .lt(3000), // Under high load, allow up to 3s
                        global().failedRequests()
                                .percent()
                                .lt(20.0) // Expect some failures due to invalid requests
                        );
    }
}
