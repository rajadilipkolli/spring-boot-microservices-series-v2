package simulation;

import static io.gatling.javaapi.http.HttpDsl.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
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
    // Kafka initialization delay in seconds (Kafka takes 5-8 seconds to initialize on first product
    // creation)
    protected static final int KAFKA_INIT_DELAY_SECONDS =
            Integer.parseInt(System.getProperty("kafkaInitDelay", "10"));

    // JSON object mapper for serialization/deserialization
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Common HTTP protocol configuration
    protected final HttpProtocolBuilder httpProtocol =
            http.baseUrl(BASE_URL)
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json")
                    .userAgentHeader("Gatling/Performance Test")
                    .disableCaching()
                    .shareConnections() // Enable connection sharing for better resource usage
                    .maxConnectionsPerHost(100) // Increase max connections
                    .connectionHeader("keep-alive")
                    .acceptEncodingHeader("gzip, deflate")
                    .enableHttp2() // Enable HTTP/2 for better performance
                    .inferHtmlResources() // Better resource inference
                    .silentResources(); // Don't log resource requests separately

    // Common data feeder for product data with address information
    protected Iterator<Map<String, Object>> enhancedProductFeeder() {
        return Stream.generate(
                        () -> {
                            ThreadLocalRandom random = ThreadLocalRandom.current();
                            Map<String, Object> data = new HashMap<>();
                            // Product information
                            data.put(
                                    "productCode",
                                    "P" + String.format("%06d", random.nextInt(10, 100_000)));
                            data.put("productName", "Product-" + random.nextInt(1000, 10000));
                            data.put("customerId", random.nextInt(1, 1000));
                            data.put("price", random.nextDouble(10, 1000));
                            data.put("quantity", random.nextInt(1, 50));

                            // Address information
                            data.put("street", "Street " + random.nextInt(1, 100));
                            data.put("city", "City " + random.nextInt(1, 20));
                            data.put(
                                    "zipCode", String.format("%05d", random.nextInt(10000, 99999)));
                            data.put("country", "Country " + random.nextInt(1, 10));

                            return data;
                        })
                .iterator();
    }

    // Common method for health checks
    protected void runHealthChecks() {
        LOGGER.info("Running health checks for services at {}", BASE_URL);

        // Define health check configurations for each service
        ServiceHealthCheck[] serviceChecks = {
            new ServiceHealthCheck("/actuator/health", "API Gateway", 15, 5000),
            new ServiceHealthCheck(
                    "/CATALOG-SERVICE/catalog-service/actuator/health",
                    "Catalog Service",
                    10,
                    3000),
            new ServiceHealthCheck(
                    "/INVENTORY-SERVICE/inventory-service/actuator/health",
                    "Inventory Service",
                    10,
                    3000),
            new ServiceHealthCheck(
                    "/ORDER-SERVICE/order-service/actuator/health", "Order Service", 10, 3000),
            new ServiceHealthCheck(
                    "/PAYMENT-SERVICE/payment-service/actuator/health", "Payment Service", 10, 3000)
        };

        // First check API Gateway as it's critical
        if (!checkServiceHealth(serviceChecks[0])) {
            String message =
                    "API Gateway is not available after "
                            + serviceChecks[0].maxRetries()
                            + " attempts. Cannot proceed with tests.";
            LOGGER.error(message);
            throw new RuntimeException(message);
        }
        LOGGER.info("API Gateway is up and running!");

        // Wait for other services to be ready
        LOGGER.info("API Gateway is up. Waiting for all services to be available...");
        boolean allServicesUp = false;
        int commonRetryCount = 0;
        final int maxCommonRetries = 10;

        while (!allServicesUp) {
            allServicesUp = true;

            // Check each service's health (skip API Gateway as it's already checked)
            for (int i = 1; i < serviceChecks.length; i++) {
                ServiceHealthCheck check = serviceChecks[i];
                if (!checkServiceHealth(check)) {
                    allServicesUp = false;
                    LOGGER.warn("{} is not yet available", check.name());
                }
            }

            if (!allServicesUp) {
                commonRetryCount++;
                if (commonRetryCount < maxCommonRetries) {
                    LOGGER.info(
                            "Not all services are up. Retry attempt {}/{}. Waiting 10 seconds...",
                            commonRetryCount,
                            maxCommonRetries);
                    try {
                        TimeUnit.SECONDS.sleep(10);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    String message =
                            "Not all services are up after " + maxCommonRetries + " attempts.";
                    LOGGER.error(message);
                    throw new RuntimeException(message);
                }
            }
        }

        if (allServicesUp) {
            LOGGER.info("All services are up and running! Proceeding with tests.");
        }
    }

    protected boolean checkServiceHealth(ServiceHealthCheck check) {
        int retryCount = 0;
        while (retryCount < check.maxRetries()) {
            try {
                LOGGER.debug(
                        "Checking {} health endpoint: {} (Attempt {}/{})",
                        check.name(),
                        check.endpoint(),
                        retryCount + 1,
                        check.maxRetries());

                HttpURLConnection connection =
                        (HttpURLConnection)
                                URI.create(BASE_URL + check.endpoint()).toURL().openConnection();
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);

                int responseCode = connection.getResponseCode();
                LOGGER.info("{} health check response: {}", check.name(), responseCode);

                if (responseCode == 200) {
                    return true;
                }

                retryCount++;
                if (retryCount < check.maxRetries()) {
                    LOGGER.warn(
                            "{} is not available (status: {}). Waiting {} ms before retry...",
                            check.name(),
                            responseCode,
                            check.retryDelayMs());
                    TimeUnit.MILLISECONDS.sleep(check.retryDelayMs());
                }
            } catch (Exception e) {
                LOGGER.warn("{} health check failed: {}", check.name(), e.getMessage());
                retryCount++;
                if (retryCount < check.maxRetries()) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(check.retryDelayMs());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return false;
    }

    // Helper method to record timing of a request
    protected void logRequestTime(String requestName, long startTimeMillis) {
        long duration = System.currentTimeMillis() - startTimeMillis;
        LOGGER.debug("Request '{}' completed in {} ms", requestName, duration);
    }

    /** Configuration class for service health check parameters. */
    public record ServiceHealthCheck(
            String endpoint, String name, int maxRetries, long retryDelayMs) {}
}
