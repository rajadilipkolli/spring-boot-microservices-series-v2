package com.example.e2e;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

import com.example.e2e.config.E2EConfig;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("e2e")
@DisplayName("E2E - Service Actuator Health Checks")
class ActuatorHealthE2ETest {

    @BeforeAll
    static void setup() {
        E2EConfig.initRestAssured();
    }

    @Test
    @DisplayName("API Gateway health endpoint should return UP")
    void gatewayShouldBeHealthy() {
        await("API Gateway health endpoint should return UP")
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () ->
                                given().spec(E2EConfig.defaultRequestSpec())
                                        .when()
                                        .get("/actuator/health")
                                        .then()
                                        .statusCode(200)
                                        .body("status", is("UP")));
    }

    @ParameterizedTest(name = "Service {0} health endpoint should be UP via Gateway")
    @ValueSource(
            strings = {
                "/catalog-service/actuator/health",
                "/inventory-service/actuator/health",
                "/order-service/actuator/health",
                "/payment-service/actuator/health"
            })
    void downstreamServicesShouldBeHealthyViaGateway(String healthPath) {
        await("Service " + healthPath + " health endpoint should be UP via Gateway")
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(2))
                .untilAsserted(
                        () ->
                                given().spec(E2EConfig.defaultRequestSpec())
                                        .when()
                                        .get(healthPath)
                                        .then()
                                        .statusCode(200)
                                        .body("status", is("UP")));
    }
}
