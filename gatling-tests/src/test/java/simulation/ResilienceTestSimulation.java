package simulation;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Choice;
import io.gatling.javaapi.core.ScenarioBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    private static final int TARGET_RATE = CONSTANT_USERS;
    private static final Duration RAMP_DURATION = Duration.ofSeconds(RAMP_DURATION_SECONDS);
    private static final Duration STEADY_STATE_DURATION = Duration.ofSeconds(TEST_DURATION_SECONDS);

    @Override
    public void before() {
        super.before(); // Run health checks
        warmUpKafka();
    }

    private void warmUpKafka() {
        try {
            LOGGER.info("Performing Kafka warm-up for resilience tests...");
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpRequest request =
                        HttpRequest.newBuilder()
                                .uri(URI.create(BASE_URL + "/catalog-service/api/catalog"))
                                .header("Content-Type", "application/json")
                                .POST(
                                        HttpRequest.BodyPublishers.ofString(
                                                """
                                {
                                  "productCode": "WARMUP-RES",
                                  "productName": "Warmup Product",
                                  "price": 10.0,
                                  "description": "Kafka Warmup"
                                }
                                """))
                                .build();
                client.send(request, HttpResponse.BodyHandlers.ofString());
                LOGGER.info("Kafka warm-up request sent.");
                Thread.sleep(5000); // Wait for Kafka init
            }
        } catch (Exception e) {
            LOGGER.warn("Kafka warm-up failed: {}", e.getMessage());
        }
    }

    // Valid data feeder
    private final Iterator<Map<String, Object>> validDataFeeder =
            Stream.generate(
                            () -> {
                                ThreadLocalRandom random = ThreadLocalRandom.current();
                                Map<String, Object> data = new HashMap<>();
                                data.put(
                                        "productCode",
                                        "P" + String.format("%06d", random.nextInt(1000, 2000)));
                                data.put("productName", "Resilience-" + random.nextInt(1, 1000));
                                data.put("price", random.nextDouble(10, 1000));
                                data.put("quantity", random.nextInt(1, 100));
                                data.put("customerId", random.nextInt(1, 100));
                                data.put("street", "Resilience St");
                                data.put("city", "Test City");
                                data.put("zipCode", "12345");
                                data.put("country", "USA");
                                return data;
                            })
                    .iterator();

    // Invalid data feeder
    private final Iterator<Map<String, Object>> invalidDataFeeder =
            Stream.generate(
                            () -> {
                                Map<String, Object> data = new HashMap<>();
                                data.put("productCode", "");
                                data.put(
                                        "productName",
                                        ThreadLocalRandom.current().nextBoolean()
                                                ? ""
                                                : "x".repeat(300));
                                data.put(
                                        "price",
                                        ThreadLocalRandom.current().nextBoolean() ? -50.0 : 0.0);
                                data.put("quantity", -10);
                                return data;
                            })
                    .iterator();

    // Test scenarios
    ChainBuilder getProductConcurrently =
            exec(
                    http("Get product concurrently")
                            .get("/catalog-service/api/catalog/P000001")
                            .check(status().in(200, 404)));

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
                            .asJson()
                            .check(status().in(400, 422)));

    ChainBuilder triggerCircuitBreaker =
            repeat(5)
                    .on(
                            exec(
                                    http("Circuit breaker probe")
                                            .get("/catalog-service/api/catalog")
                                            .check(status().saveAs("responseStatus"))));

    ScenarioBuilder resilienceScenario =
            scenario("Resilience Test Workflow")
                    .feed(validDataFeeder)
                    .randomSwitch()
                    .on(
                            new Choice.WithWeight(40.0, ScenarioBuilders.createProductChain()),
                            new Choice.WithWeight(20.0, createInvalidProduct),
                            new Choice.WithWeight(20.0, getProductConcurrently),
                            new Choice.WithWeight(20.0, triggerCircuitBreaker));

    public ResilienceTestSimulation() {
        LOGGER.info("Starting ResilienceTestSimulation with 3-phase injection profile");

        this.setUp(
                        resilienceScenario.injectOpen(
                                rampUsersPerSec(0).to(TARGET_RATE).during(RAMP_DURATION),
                                constantUsersPerSec(TARGET_RATE).during(STEADY_STATE_DURATION),
                                rampUsersPerSec(TARGET_RATE).to(0).during(RAMP_DURATION)))
                .protocols(httpProtocol)
                .maxDuration(
                        RAMP_DURATION
                                .plus(STEADY_STATE_DURATION)
                                .plus(RAMP_DURATION)
                                .plus(Duration.ofMinutes(1)))
                .assertions(
                        global().responseTime().percentile(95).lt(3000),
                        global().failedRequests()
                                .percent()
                                .lt(25.0) // Expected failures in resilience test
                        );
    }
}
