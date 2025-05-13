package simulation;

import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all Gatling simulations in the project. Provides common configuration, feeders,
 * and utility methods.
 */
public abstract class BaseSimulation extends Simulation {

    protected static final Logger LOGGER = LoggerFactory.getLogger(BaseSimulation.class);

    // Common system properties for all simulations
    protected static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8765");

    // Common HTTP protocol configuration
    protected final HttpProtocolBuilder httpProtocol =
            http.baseUrl(BASE_URL)
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json")
                    .userAgentHeader("Gatling/Performance Test")
                    .disableCaching();

    // Common data feeder for product data
    protected Iterator<Map<String, Object>> productFeeder(int max) {
        return Stream.generate(
                        () -> {
                            ThreadLocalRandom random = ThreadLocalRandom.current();
                            Map<String, Object> data = new HashMap<>();
                            data.put(
                                    "productCode",
                                    "P" + String.format("%06d", random.nextInt(1, max)));
                            data.put("productName", "Product-" + random.nextInt(1, max));
                            data.put("price", random.nextDouble(10, 1000));
                            data.put("quantity", random.nextInt(1, 50));
                            data.put("customerId", random.nextInt(1, 100));
                            return data;
                        })
                .iterator();
    }

    // Common method for health checks
    protected void runHealthChecks() {
        LOGGER.info("Running health checks for services at {}", BASE_URL);

        try {
            // Use a simple HTTP client to check if API Gateway is up
            HttpURLConnection connection =
                    (HttpURLConnection)
                            URI.create(BASE_URL + "/actuator/health").toURL().openConnection();
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            LOGGER.info("API Gateway health check response: {}", responseCode);
        } catch (Exception e) {
            LOGGER.warn(
                    "Health check failed: {}. Tests may fail if services are not running.",
                    e.getMessage());
        }
    }

    // Helper method to record timing of a request
    protected void logRequestTime(String requestName, long startTimeMillis) {
        long duration = System.currentTimeMillis() - startTimeMillis;
        LOGGER.debug("Request '{}' completed in {} ms", requestName, duration);
    }
}
