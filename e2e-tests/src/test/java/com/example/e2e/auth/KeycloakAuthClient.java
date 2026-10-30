package com.example.e2e.auth;

import static io.restassured.RestAssured.given;

import com.example.e2e.config.E2EConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KeycloakAuthClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAuthClient.class);

    private static final String DEFAULT_REALM = "retailstore";
    private static final String DEFAULT_CLIENT_ID = "retailstore-webapp";
    private static final String DEFAULT_USERNAME = "retail";

    private record CachedToken(String token, Instant expiry) {}

    private static final Map<String, CachedToken> TOKEN_CACHE = new ConcurrentHashMap<>();

    private KeycloakAuthClient() {}

    /**
     * Acquires an OAuth2 access token for the default retail user (with ADMIN and OPERATOR client
     * roles) using the Resource Owner Password Credentials (ROPC) grant.
     *
     * @return a valid bearer access token, or {@code null} if acquisition failed
     */
    public static String getAccessToken() {
        return getAccessToken(DEFAULT_USERNAME, E2EConfig.getKeycloakPassword());
    }

    /**
     * Acquires an OAuth2 access token for a specific username and password using the Resource Owner
     * Password Credentials grant against the {@code retailstore-webapp} client.
     *
     * @param username the Keycloak username
     * @param password the user password
     * @return a valid bearer access token, or {@code null} if acquisition failed
     */
    public static synchronized String getAccessToken(String username, String password) {
        String cacheKey = "user:" + username;
        CachedToken cached = TOKEN_CACHE.get(cacheKey);
        if (cached != null && Instant.now().isBefore(cached.expiry())) {
            return cached.token();
        }

        String keycloakUrl = E2EConfig.getKeycloakUrl();
        String tokenEndpoint =
                keycloakUrl + "/realms/" + DEFAULT_REALM + "/protocol/openid-connect/token";

        log.info(
                "Requesting Keycloak user access token for '{}' from: {}", username, tokenEndpoint);

        try {
            Response response =
                    given().contentType(ContentType.URLENC)
                            .formParam("client_id", DEFAULT_CLIENT_ID)
                            .formParam("client_secret", E2EConfig.getKeycloakClientSecret())
                            .formParam("grant_type", "password")
                            .formParam("username", username)
                            .formParam("password", password)
                            .when()
                            .post(tokenEndpoint)
                            .then()
                            .extract()
                            .response();

            if (response.statusCode() == 200) {
                String token = response.jsonPath().getString("access_token");
                int expiresIn = response.jsonPath().getInt("expires_in");
                Instant expiry = Instant.now().plusSeconds(Math.max(10, expiresIn - 30));
                TOKEN_CACHE.put(cacheKey, new CachedToken(token, expiry));
                log.info("Successfully acquired Keycloak access token for user '{}'", username);
                return token;
            } else {
                log.warn(
                        "Failed to acquire Keycloak token for '{}'. Status: {}, Body: {}",
                        username,
                        response.statusCode(),
                        response.asString());
            }
        } catch (Exception ex) {
            log.warn(
                    "Keycloak token request for '{}' threw exception: {}",
                    username,
                    ex.getMessage());
        }

        return null;
    }

    /**
     * Acquires an OAuth2 access token using the Client Credentials grant for the {@code
     * retailstore-webapp} confidential client.
     *
     * @return a valid client access token, or {@code null} if acquisition failed
     */
    public static synchronized String getClientCredentialsToken() {
        String cacheKey = "client:" + DEFAULT_CLIENT_ID;
        CachedToken cached = TOKEN_CACHE.get(cacheKey);
        if (cached != null && Instant.now().isBefore(cached.expiry())) {
            return cached.token();
        }

        String keycloakUrl = E2EConfig.getKeycloakUrl();
        String tokenEndpoint =
                keycloakUrl + "/realms/" + DEFAULT_REALM + "/protocol/openid-connect/token";

        log.info(
                "Requesting Keycloak client_credentials token for client '{}' from: {}",
                DEFAULT_CLIENT_ID,
                tokenEndpoint);

        try {
            Response response =
                    given().contentType(ContentType.URLENC)
                            .formParam("client_id", DEFAULT_CLIENT_ID)
                            .formParam("client_secret", E2EConfig.getKeycloakClientSecret())
                            .formParam("grant_type", "client_credentials")
                            .when()
                            .post(tokenEndpoint)
                            .then()
                            .extract()
                            .response();

            if (response.statusCode() == 200) {
                String token = response.jsonPath().getString("access_token");
                int expiresIn = response.jsonPath().getInt("expires_in");
                Instant expiry = Instant.now().plusSeconds(Math.max(10, expiresIn - 30));
                TOKEN_CACHE.put(cacheKey, new CachedToken(token, expiry));
                log.info("Successfully acquired Keycloak client_credentials access token");
                return token;
            } else {
                log.warn(
                        "Failed to acquire client_credentials token. Status: {}, Body: {}",
                        response.statusCode(),
                        response.asString());
            }
        } catch (Exception ex) {
            log.warn("Keycloak client_credentials request threw exception: {}", ex.getMessage());
        }

        return null;
    }
}
