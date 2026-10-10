package com.example.retailstore.webapp.web.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.retailstore.webapp.clients.customer.CustomerRequest;
import com.example.retailstore.webapp.clients.customer.CustomerResponse;
import com.example.retailstore.webapp.common.AbstractIntegrationTest;
import com.example.retailstore.webapp.model.request.RegistrationRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.core.JacksonException;

class RegistrationControllerIT extends AbstractIntegrationTest {

    // The realm name should match the one configured in KeycloakContainer and used by the application
    private static final String REALM_NAME = "retailstore";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_EMAIL = "testEmail@email.com";
    private static final String TEST_FIRST_NAME = "firstName";
    private static final String TEST_LAST_NAME = "lastName";
    private static final Long TEST_PHONE_NUMBER = 1234567890L;
    private static final String TEST_ADDRESS_LINE_1 = "Test Address";
    private static final String TEST_ADDRESS_LINE_2 = null;
    private static final String TEST_CITY = "Test City";
    private static final String TEST_STATE = "Test State";
    private static final String TEST_ZIP_CODE = "12345";
    private static final String TEST_COUNTRY = "Test Country";
    private static final String CUSTOMER_SERVICE_API_PATH = "/payment-service/api/customers";

    /** Removes accounts created by successful and duplicate registration scenarios after each test. */
    @AfterEach
    void cleanupUsers() {
        deleteUsers(TEST_USERNAME);
        deleteUsers("existinguser");
    }

    /** Verifies that registration creates a Keycloak user and forwards customer details to payment. */
    @Test
    void testRegister() throws JacksonException {

        RegistrationRequest registrationRequest = new RegistrationRequest(
                TEST_USERNAME,
                TEST_EMAIL,
                TEST_FIRST_NAME,
                TEST_LAST_NAME,
                "Test@1234",
                TEST_PHONE_NUMBER,
                TEST_ADDRESS_LINE_1,
                TEST_ADDRESS_LINE_2,
                TEST_CITY,
                TEST_STATE,
                TEST_ZIP_CODE,
                TEST_COUNTRY);

        // Arrange: Expected CustomerRequest and CustomerResponse for mocking CustomerServiceClient
        CustomerRequest expectedCustomerRequest = new CustomerRequest(
                TEST_USERNAME,
                TEST_EMAIL,
                String.valueOf(TEST_PHONE_NUMBER),
                TEST_ADDRESS_LINE_1,
                TEST_ADDRESS_LINE_2,
                TEST_CITY,
                TEST_STATE,
                TEST_ZIP_CODE,
                TEST_COUNTRY,
                BigDecimal.valueOf(10_000));
        CustomerResponse expectedCustomerResponse = new CustomerResponse(
                1L,
                TEST_USERNAME,
                TEST_EMAIL,
                String.valueOf(TEST_PHONE_NUMBER),
                TEST_ADDRESS_LINE_1,
                TEST_ADDRESS_LINE_2,
                TEST_CITY,
                TEST_STATE,
                TEST_ZIP_CODE,
                TEST_COUNTRY,
                BigDecimal.valueOf(10_000));

        // Arrange: Stub for CustomerServiceClient call via gatewayServiceMock
        gatewayServiceMock.stubFor(post(urlEqualTo(CUSTOMER_SERVICE_API_PATH))
                .withRequestBody(equalToJson(jsonMapper.writeValueAsString(expectedCustomerRequest)))
                .willReturn(aResponse()
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jsonMapper.writeValueAsString(expectedCustomerResponse))));

        // Act
        mockMvcTester
                .post()
                .uri("/api/register")
                .content(jsonMapper.writeValueAsString(registrationRequest))
                .contentType(MediaType.APPLICATION_JSON)
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.message")
                .isEqualTo("User registered successfully");

        // Verify user creation in Keycloak
        Keycloak keycloakAdminClient = keycloakContainer.getKeycloakAdminClient();
        List<UserRepresentation> users =
                keycloakAdminClient.realm(REALM_NAME).users().searchByUsername(TEST_USERNAME, true);
        assertThat(users).hasSize(1);
        UserRepresentation user = users.getFirst();
        assertThat(user.getUsername()).isEqualTo(TEST_USERNAME);
        assertThat(user.getEmail()).isEqualToIgnoringCase(TEST_EMAIL);
        assertThat(user.getFirstName()).isEqualTo(TEST_FIRST_NAME);
        assertThat(user.getLastName()).isEqualTo(TEST_LAST_NAME);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.isEmailVerified()).isFalse(); // Typically email is not verified immediately

        // User search results do not include role mappings; query the assigned realm roles directly.
        assertThat(keycloakAdminClient
                        .realm(REALM_NAME)
                        .users()
                        .get(user.getId())
                        .roles()
                        .realmLevel()
                        .listAll())
                .extracting(RoleRepresentation::getName)
                .contains("user");

        // Assert: Verify that the CustomerService was called
        gatewayServiceMock.verify(
                1, // Ensure it was called exactly once
                postRequestedFor(urlEqualTo(CUSTOMER_SERVICE_API_PATH))
                        .withRequestBody(equalToJson(jsonMapper.writeValueAsString(expectedCustomerRequest))));
    }

    /** Verifies that a username shorter than the allowed minimum produces HTTP 400. */
    @Test
    void shouldReturnBadRequestForInvalidUsername() {
        RegistrationRequest request = new RegistrationRequest(
                "u", // invalid username (too short)
                "test@example.com",
                "Test",
                "User",
                "Password123@",
                TEST_PHONE_NUMBER,
                TEST_ADDRESS_LINE_1,
                TEST_ADDRESS_LINE_2,
                TEST_CITY,
                TEST_STATE,
                TEST_ZIP_CODE,
                TEST_COUNTRY);

        mockMvcTester
                .post()
                .uri("/api/register")
                .content(jsonMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON)
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .isEqualTo("Invalid request content.");
    }

    /** Verifies that a password lacking required character types produces HTTP 400. */
    @Test
    void shouldReturnBadRequestForInvalidPassword() {
        RegistrationRequest request = new RegistrationRequest(
                "testuser",
                "test@example.com",
                "Test",
                "User",
                "password",
                TEST_PHONE_NUMBER,
                TEST_ADDRESS_LINE_1,
                TEST_ADDRESS_LINE_2,
                TEST_CITY,
                TEST_STATE,
                TEST_ZIP_CODE,
                TEST_COUNTRY); // invalid password (no uppercase, numbers, or special chars)

        mockMvcTester
                .post()
                .uri("/api/register")
                .content(jsonMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON)
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .isEqualTo("Invalid request content.");
    }

    /** Verifies that a malformed email address produces HTTP 400. */
    @Test
    void shouldReturnBadRequestForInvalidEmail() {
        RegistrationRequest request = new RegistrationRequest(
                "testuser",
                "invalid-email", // invalid email format
                "Test",
                "User",
                "Password123@",
                TEST_PHONE_NUMBER,
                TEST_ADDRESS_LINE_1,
                TEST_ADDRESS_LINE_2,
                TEST_CITY,
                TEST_STATE,
                TEST_ZIP_CODE,
                TEST_COUNTRY);

        mockMvcTester
                .post()
                .uri("/api/register")
                .content(jsonMapper.writeValueAsString(request))
                .contentType(MediaType.APPLICATION_JSON)
                .assertThat()
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .isEqualTo("Invalid request content.");
    }

    /** Verifies that a duplicate username produces HTTP 409 Conflict. */
    @Test
    void shouldReturnConflictWhenUserAlreadyExists() {
        RegistrationRequest registrationRequest = new RegistrationRequest(
                "existinguser",
                "existing@example.com",
                "Test",
                "User",
                "Password123@",
                1234567890L,
                "Test Address",
                null,
                "Test City",
                "Test State",
                "12345",
                "Test Country");

        CustomerRequest expectedCustomerRequest = new CustomerRequest(
                "existinguser",
                "existing@example.com",
                "1234567890",
                "Test Address",
                null,
                "Test City",
                "Test State",
                "12345",
                "Test Country",
                BigDecimal.valueOf(10_000));

        CustomerResponse expectedCustomerResponse = new CustomerResponse(
                1L,
                "existinguser",
                "existing@example.com",
                "1234567890",
                "Test Address",
                null,
                "Test City",
                "Test State",
                "12345",
                "Test Country",
                BigDecimal.valueOf(10_000));

        gatewayServiceMock.stubFor(post(urlEqualTo(CUSTOMER_SERVICE_API_PATH))
                .withRequestBody(equalToJson(jsonMapper.writeValueAsString(expectedCustomerRequest)))
                .willReturn(aResponse()
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(jsonMapper.writeValueAsString(expectedCustomerResponse))));

        // First registration
        mockMvcTester
                .post()
                .uri("/api/register")
                .content(jsonMapper.writeValueAsString(registrationRequest))
                .contentType(MediaType.APPLICATION_JSON)
                .assertThat()
                .hasStatus(HttpStatus.OK);

        // Second registration should fail with 409 Conflict
        mockMvcTester
                .post()
                .uri("/api/register")
                .content(jsonMapper.writeValueAsString(registrationRequest))
                .contentType(MediaType.APPLICATION_JSON)
                .assertThat()
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail")
                .asString()
                .contains("already taken");
    }

    /**
     * Deletes test accounts whose usernames exactly match the supplied value.
     *
     * @param username username to remove from the test realm
     */
    private void deleteUsers(String username) {
        // Clean up Keycloak
        Keycloak keycloakAdminClient = keycloakContainer.getKeycloakAdminClient();
        List<UserRepresentation> users =
                keycloakAdminClient.realm(REALM_NAME).users().searchByUsername(username, true);
        if (!users.isEmpty()) {
            for (UserRepresentation user : users) {
                if (username.equals(user.getUsername())) {
                    keycloakAdminClient.realm(REALM_NAME).users().delete(user.getId());
                }
            }
        }
    }
}
