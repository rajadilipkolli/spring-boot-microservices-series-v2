package com.example.e2e.auth;

import static io.restassured.RestAssured.given;

import com.example.e2e.config.E2EConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KeycloakAuthClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthClient.class);

    private static final String DEFAULT_REALM = "retailstore";
    private static final String DEFAULT_CLIENT_ID = "retailstore-webapp";
    private static final String DEFAULT_USERNAME = "retail";

    private static String cachedToken;
    private static Instant tokenExpiry = Instant.MIN;

    private KeycloakAuthClient() {}

    public static synchronized String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }

        String keycloakUrl = E2EConfig.getKeycloakUrl();
        String tokenEndpoint =
                keycloakUrl + "/realms/" + DEFAULT_REALM + "/protocol/openid-connect/token";

        log.info("Requesting Keycloak OAuth2 token from: {}", tokenEndpoint);

        try {
            Response response =
                    given().contentType(ContentType.URLENC)
                            .formParam("client_id", DEFAULT_CLIENT_ID)
                            .formParam("client_secret", E2EConfig.getKeycloakClientSecret())
                            .formParam("grant_type", "password")
                            .formParam("username", DEFAULT_USERNAME)
                            .formParam("password", E2EConfig.getKeycloakPassword())
                            .when()
                            .post(tokenEndpoint)
                            .then()
                            .extract()
                            .response();

            if (response.statusCode() == 200) {
                cachedToken = response.jsonPath().getString("access_token");
                int expiresIn = response.jsonPath().getInt("expires_in");
                tokenExpiry = Instant.now().plusSeconds(Math.max(10, expiresIn - 30));
                log.info("Successfully acquired Keycloak access token");
                return cachedToken;
            } else {
                log.warn(
                        "Failed to acquire Keycloak token. Status: {}, Body: {}",
                        response.statusCode(),
                        response.asString());
            }
        } catch (Exception ex) {
            log.warn("Keycloak token request threw exception: {}", ex.getMessage());
        }

        return null;
    }
}
