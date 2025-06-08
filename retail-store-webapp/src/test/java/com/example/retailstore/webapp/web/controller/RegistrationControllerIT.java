package com.example.retailstore.webapp.web.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.retailstore.webapp.clients.customer.CustomerRequest;
import com.example.retailstore.webapp.clients.customer.CustomerResponse;
import com.example.retailstore.webapp.common.AbstractIntegrationTest;
import com.example.retailstore.webapp.web.model.request.RegistrationRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class RegistrationControllerIT extends AbstractIntegrationTest {

    @Autowired
    private KeycloakContainer keycloakContainer;

    // The realm name should match the one configured in KeycloakContainer and used by the application
    private static final String REALM_NAME = "retailstore";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_EMAIL = "testEmail@email.com";
    private static final String TEST_FIRST_NAME = "firstName";
    private static final String TEST_LAST_NAME = "lastName";
    private static final Long TEST_PHONE_NUMBER = 1234567890L;
    private static final String TEST_ADDRESS_LINE = "Test Address";
    private static final String CUSTOMER_SERVICE_API_PATH = "/payment-service/api/customers";

    @Test
    void testRegister() throws JsonProcessingException {

        RegistrationRequest registrationRequest = new RegistrationRequest(
                TEST_USERNAME,
                TEST_EMAIL,
                TEST_FIRST_NAME,
                TEST_LAST_NAME,
                "Test@1234",
                TEST_PHONE_NUMBER,
                TEST_ADDRESS_LINE);

        // Arrange: Expected CustomerRequest and CustomerResponse for mocking CustomerServiceClient
        CustomerRequest expectedCustomerRequest = new CustomerRequest(
                TEST_USERNAME, TEST_EMAIL, String.valueOf(TEST_PHONE_NUMBER), TEST_ADDRESS_LINE, 10_000);
        CustomerResponse expectedCustomerResponse = new CustomerResponse(
                1L, TEST_USERNAME, TEST_EMAIL, String.valueOf(TEST_PHONE_NUMBER), TEST_ADDRESS_LINE, 10_000);

        // Arrange: Stub for CustomerServiceClient call via gatewayServiceMock
        gatewayServiceMock.stubFor(post(urlEqualTo(CUSTOMER_SERVICE_API_PATH))
                .withRequestBody(equalToJson(objectMapper.writeValueAsString(expectedCustomerRequest)))
                .willReturn(aResponse()
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(expectedCustomerResponse))));

        // Act
        mockMvcTester
                .post()
                .uri("/api/register")
                .content(objectMapper.writeValueAsString(registrationRequest))
                .contentType(MediaType.APPLICATION_JSON)
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.message")
                .isEqualTo("User registered successfully");

        // Verify user creation in Keycloak
        Keycloak keycloakAdminClient = keycloakContainer.getKeycloakAdminClient();
        List<UserRepresentation> users =
                keycloakAdminClient.realm(REALM_NAME).users().search(TEST_USERNAME, true);
        assertThat(users).hasSize(1);
        UserRepresentation user = users.getFirst();
        assertThat(user.getUsername()).isEqualTo(TEST_USERNAME);
        assertThat(user.getEmail()).isEqualToIgnoringCase(TEST_EMAIL);
        assertThat(user.getFirstName()).isEqualTo(TEST_FIRST_NAME);
        assertThat(user.getLastName()).isEqualTo(TEST_LAST_NAME);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.isEmailVerified()).isFalse(); // Typically email is not verified immediately

        // Assert: Verify that the CustomerService was called
        gatewayServiceMock.verify(
                1, // Ensure it was called exactly once
                postRequestedFor(urlEqualTo(CUSTOMER_SERVICE_API_PATH))
                        .withRequestBody(equalToJson(objectMapper.writeValueAsString(expectedCustomerRequest))));
    }

    @AfterEach
    void tearDown() {
        // Clean up the created user in Keycloak to ensure test idempotency
        Keycloak keycloakAdminClient = keycloakContainer.getKeycloakAdminClient();
        List<UserRepresentation> users =
                keycloakAdminClient.realm(REALM_NAME).users().search(TEST_USERNAME, true);
        if (!users.isEmpty()) {
            for (UserRepresentation user : users) {
                if (TEST_USERNAME.equals(user.getUsername())) {
                    keycloakAdminClient.realm(REALM_NAME).users().delete(user.getId());
                }
            }
        }
    }
}
