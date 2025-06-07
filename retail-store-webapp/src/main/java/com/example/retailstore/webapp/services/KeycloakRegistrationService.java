package com.example.retailstore.webapp.services;

import com.example.retailstore.webapp.exception.KeyCloakException;
import com.example.retailstore.webapp.web.model.request.RegistrationRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class KeycloakRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakRegistrationService.class);

    private final String keycloakUrl;
    private final String realm;
    private final RestClient restClient;

    @Autowired
    public KeycloakRegistrationService(@Value("${OAUTH2_SERVER_URL}") String keycloakUrl) {
        this(keycloakUrl, RestClient.create());
    }

    // Constructor for testing
    protected KeycloakRegistrationService(String keycloakUrl, RestClient restClient) {
        this.keycloakUrl = keycloakUrl;
        this.realm = "retailstore";
        this.restClient = restClient;
    }

    private String getAdminToken() {
        var formData = "grant_type=password&client_id=admin-cli&username=admin&password=admin1234";

        var response = restClient
                .post()
                .uri(keycloakUrl + "/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(Map.class);

        if (response == null || !response.containsKey("access_token")) {
            throw new RuntimeException("Failed to obtain access token");
        }

        return (String) response.get("access_token");
    }

    public void registerUser(RegistrationRequest request) {
        try {
            logger.info("Registering new user: {}", request.username());
            // First, get an admin access token
            String adminToken = getAdminToken();

            logger.debug("Admin token obtained successfully");
            // Create the user in Keycloak with USER role
            restClient
                    .post()
                    .uri(keycloakUrl + "/admin/realms/" + realm + "/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + adminToken)
                    .body(Map.of(
                            "username", request.username(),
                            "email", request.email(),
                            "enabled", true,
                            "firstName", request.firstName(),
                            "lastName", request.lastName(),
                            "credentials",
                                    List.of(Map.of(
                                            "type", "password", "value", request.password(), "temporary", false)),
                            "groups", List.of("Users"), // Adding to Users group
                            "realmRoles", List.of("USER") // Assigning USER role
                            ))
                    .retrieve()
                    .toBodilessEntity();
            logger.info("User {} registered successfully", request.username());
        } catch (Exception e) {
            logger.error("Failed to register user {} : {}", request.username(), e.getMessage(), e);
            throw new KeyCloakException(e.getMessage());
        }
    }
}
