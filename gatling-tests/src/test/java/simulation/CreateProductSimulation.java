package simulation;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.bodyString;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gatling.javaapi.core.CoreDsl;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateProductSimulation extends Simulation {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateProductSimulation.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    HttpProtocolBuilder httpProtocol =
            http.baseUrl("http://localhost:8765")
                    .acceptHeader("application/json")
                    .userAgentHeader("Gatling/Performance Test");

    Iterator<Map<String, Object>> feeder =
            Stream.generate(
                            () -> {
                                Map<String, Object> objectMap = new HashMap<>();
                                objectMap.put(
                                        "code", "Product" + new SecureRandom().nextInt(101, 200));
                                objectMap.put(
                                        "productName",
                                        "Product Name " + new SecureRandom().nextInt());
                                objectMap.put("price", new SecureRandom().nextLong(10, 100));
                                objectMap.put("customerId", new SecureRandom().nextInt(1, 100));
                                return objectMap;
                            })
                    .iterator();

    ScenarioBuilder scn =
            CoreDsl.scenario("Load Test Creating Products")
                    .feed(feeder)
                    .exec(
                            http("create-product-request")
                                    .post("/catalog-service/api/catalog")
                                    .header("Content-Type", "application/json")
                                    .body(
                                            StringBody(
                                                    "{ \"code\": \"#{code}\",\"productName\":\"#{productName}\",\"price\":#{price}, \"description\": \"A Beautiful Product\" }"))
                                    .check(status().is(201))
                                    .check(header("location").saveAs("location")))
                    .exec(
                            http("get-product-request")
                                    .get(
                                            session ->
                                                    "/catalog-service"
                                                            + session.getString("location"))
                                    .check(status().is(200)))
                    .exec(
                            http("get-inventory-request")
                                    .get("/inventory-service/api/inventory/#{code}")
                                    .check(status().is(200))
                                    .check(bodyString().saveAs("inventoryResponseBody")))
                    .exec(
                            http("update-inventory-request")
                                    .put(
                                            session ->
                                                    "/inventory-service/api/inventory/"
                                                            + getInventoryId(
                                                                    session.get(
                                                                            "inventoryResponseBody")))
                                    .header("Content-Type", "application/json")
                                    .body(
                                            StringBody(
                                                    session ->
                                                            getBodyAsString(
                                                                    session.get(
                                                                            "inventoryResponseBody"))))
                                    .check(status().is(200)))
                    .exec(
                            http("create-order-request")
                                    .post("/order-service/api/orders")
                                    .header("Content-Type", "application/json")
                                    .body(
                                            StringBody(
                                                    """
                                                    {
                                                      "customerId": #{customerId},
                                                      "items": [
                                                        {
                                                          "productId": "#{code}",
                                                          "quantity": 10,
                                                          "productPrice": 5
                                                        }
                                                      ]
                                                    }
                                                    """))
                                    .asJson()
                                    .check(status().is(201))
                                    .check(header("location").saveAs("location")));

    @SneakyThrows
    private String getBodyAsString(String inventoryResponseBody) {
        InventoryResponseDTO inventoryResponseDTO =
                OBJECT_MAPPER.readValue(inventoryResponseBody, InventoryResponseDTO.class);
        int nextInt = new SecureRandom().nextInt(100, 1000);
        String body =
                OBJECT_MAPPER.writeValueAsString(
                        inventoryResponseDTO.withAvailableQuantity(nextInt));
        LOGGER.info("Update Inventory Request :{}", body);
        return body;
    }

    @lombok.SneakyThrows
    private Long getInventoryId(String inventoryResponseBody) {
        InventoryResponseDTO inventoryResponseDTO =
                OBJECT_MAPPER.readValue(inventoryResponseBody, InventoryResponseDTO.class);
        return inventoryResponseDTO.id();
    }

    public CreateProductSimulation() {
        this.setUp(
                        scn.pause(Duration.ofSeconds(1))
                                .injectOpen(constantUsersPerSec(5).during(Duration.ofSeconds(30))))
                .protocols(httpProtocol);
    }
}
