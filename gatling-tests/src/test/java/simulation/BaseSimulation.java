package simulation;

import static io.gatling.javaapi.http.HttpDsl.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.io.IOException;
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
            Integer.parseInt(System.getProperty("kafkaInitDelay", "15"));

    // JSON object mapper for serialization/deserialization
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Common HTTP protocol configuration
    protected final HttpProtocolBuilder httpProtocol =
            http.baseUrl(BASE_URL)
                    .acceptHeader("application/json")
                    .contentTypeHeader("application/json")
                    .userAgentHeader("Gatling/Performance Test")
                    .disableCaching();

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

        // List of all service health endpoints to check
        String[] serviceEndpoints = {
            "/actuator/health", // API Gateway
            "/CATALOG-SERVICE/catalog-service/actuator/health", // Catalog Service
            "/INVENTORY-SERVICE/inventory-service/actuator/health", // Inventory Service
            "/ORDER-SERVICE/order-service/actuator/health", // Order Service
            "/PAYMENT-SERVICE/payment-service/actuator/health" // Payment Service
        };

        // First check API Gateway with retries
        int maxGatewayRetries = 15; // More retries for gateway as it's critical
        int gatewayRetryCount = 0;
        boolean apiGatewayUp = false;

        while (!apiGatewayUp && gatewayRetryCount < maxGatewayRetries) {
            try {
                LOGGER.info(
                        "Checking API Gateway health, attempt {}/{}",
                        gatewayRetryCount + 1,
                        maxGatewayRetries);
                apiGatewayUp = checkServiceHealth(serviceEndpoints[0], "API Gateway");

                if (apiGatewayUp) {
                    LOGGER.info("API Gateway is up and running!");
                } else {
                    gatewayRetryCount++;
                    if (gatewayRetryCount < maxGatewayRetries) {
                        LOGGER.warn(
                                "API Gateway is not yet available. Waiting 5 seconds before retry...");
                        try {
                            TimeUnit.SECONDS.sleep(5);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            } catch (Exception e) {
                gatewayRetryCount++;
                if (gatewayRetryCount < maxGatewayRetries) {
                    LOGGER.warn(
                            "Error checking API Gateway health: {}. Waiting 5 seconds before retry...",
                            e.getMessage());
                    try {
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    LOGGER.error("Error checking API Gateway health: {}", e.getMessage());
                }
            }
        }

        // If API Gateway is still not up after all retries, throw an exception
        if (!apiGatewayUp) {
            LOGGER.error(
                    "API Gateway is not available after {} attempts. Cannot proceed with tests.",
                    maxGatewayRetries);
            throw new RuntimeException(
                    "API Gateway is not available after multiple attempts. Cannot proceed with tests.");
        }

        // Wait for services to be ready
        LOGGER.info("API Gateway is up. Waiting for all services to be available...");

        // Try multiple times with a delay between attempts
        int maxRetries = 10;
        int retryCount = 0;
        boolean allUp = false;

        while (!allUp) {
            allUp = true;

            // Check each service's health
            for (int i = 1; i < serviceEndpoints.length; i++) {
                String endpoint = serviceEndpoints[i];
                String serviceName = endpoint.split("/")[1]; // Extract service name for logging

                try {
                    boolean serviceUp = checkServiceHealth(endpoint, serviceName);
                    if (!serviceUp) {
                        allUp = false;
                        LOGGER.warn("{} is not yet available", serviceName);
                    }
                } catch (Exception e) {
                    allUp = false;
                    LOGGER.warn("Error checking {} health: {}", serviceName, e.getMessage());
                }
            }

            if (!allUp) {
                retryCount++;
                if (retryCount < maxRetries) {
                    LOGGER.info(
                            "Not all services are up. Retry attempt {}/{}. Waiting 10 seconds...",
                            retryCount,
                            maxRetries);
                    try {
                        TimeUnit.SECONDS.sleep(10); // Wait 10 seconds before retrying
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    LOGGER.error(
                            "Not all services are up after {} attempts. Cannot proceed with tests.",
                            maxRetries);
                    throw new RuntimeException(
                            "Not all services are up after multiple attempts. Cannot proceed with tests.");
                }
            }
        }

        LOGGER.info("All services are up and running! Proceeding with tests.");
    }

    /**
     * Helper method to check the health of a specific service
     *
     * @param endpoint The health endpoint to check
     * @param serviceName The name of the service (for logging)
     * @return true if the service is up and healthy, false otherwise
     */
    private boolean checkServiceHealth(String endpoint, String serviceName) throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) URI.create(BASE_URL + endpoint).toURL().openConnection();
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);

        int responseCode = connection.getResponseCode();
        LOGGER.info("{} health check response: {}", serviceName, responseCode);

        return responseCode == 200;
    }

    // Helper method to record timing of a request
    protected void logRequestTime(String requestName, long startTimeMillis) {
        long duration = System.currentTimeMillis() - startTimeMillis;
        LOGGER.debug("Request '{}' completed in {} ms", requestName, duration);
    }
}
