package com.example.e2e;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import com.example.e2e.auth.KeycloakAuthClient;
import com.example.e2e.config.E2EConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("e2e")
@DisplayName("E2E - Keycloak Authentication & OAuth2 Validation")
class KeycloakSecurityE2ETest {

    @BeforeAll
    static void setup() {
        E2EConfig.initRestAssured();
    }

    @Test
    @DisplayName("Should successfully authenticate with Keycloak and acquire access token")
    void shouldAcquireKeycloakAccessToken() {
        String token = KeycloakAuthClient.getAccessToken();
        assertThat(token).as("Keycloak OAuth2 token should not be null").isNotBlank();
    }

    @Test
    @DisplayName("Should query OpenID configuration from Keycloak realm")
    void shouldExposeOpenIdConfiguration() {
        String keycloakUrl = E2EConfig.getKeycloakUrl();
        given().when()
                .get(keycloakUrl + "/realms/retailstore/.well-known/openid-configuration")
                .then()
                .statusCode(200)
                .body("issuer", notNullValue())
                .body("token_endpoint", notNullValue());
    }
}
