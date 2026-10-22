package com.example.e2e;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import com.example.e2e.config.E2EConfig;
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
        given().spec(E2EConfig.defaultRequestSpec())
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"));
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
        given().spec(E2EConfig.defaultRequestSpec())
                .when()
                .get(healthPath)
                .then()
                .statusCode(200)
                .body("status", is("UP"));
    }
}
