package com.example.retailstore.webapp.web.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.retailstore.webapp.clients.customer.CustomerRequest;
import com.example.retailstore.webapp.clients.customer.CustomerResponse;
import com.example.retailstore.webapp.clients.customer.CustomerServiceClient;
import com.example.retailstore.webapp.config.SecurityConfig;
import com.example.retailstore.webapp.exception.KeyCloakException;
import com.example.retailstore.webapp.model.request.RegistrationRequest;
import com.example.retailstore.webapp.services.KeycloakRegistrationService;
import com.example.retailstore.webapp.services.SecurityHelper;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(RegistrationController.class)
@Import({SecurityConfig.class, SecurityHelper.class})
class RegistrationControllerTest {

    private static final String REGISTER_ENDPOINT = "/api/register";
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "AbcXyz@123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private KeycloakRegistrationService registrationService;

    @MockitoBean
    private CustomerServiceClient customerServiceClient;

    /** Verifies that anonymous registration returns a success message when both service calls succeed. */
    @Test
    @WithAnonymousUser
    void shouldRegisterUserSuccessfully() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                TEST_USERNAME,
                TEST_EMAIL,
                "Test",
                "User",
                TEST_PASSWORD,
                9848022334L,
                "junitAddress",
                null,
                "Test City",
                "Test State",
                "12345",
                "Test Country");
        doNothing().when(registrationService).registerUser(any(RegistrationRequest.class));
        // Mock CustomerServiceClient to return a valid CustomerResponse
        when(customerServiceClient.getOrCreateCustomer(any(CustomerRequest.class)))
                .thenReturn(new CustomerResponse(
                        1L,
                        TEST_USERNAME,
                        TEST_EMAIL,
                        "9848022334",
                        "junitAddress",
                        null,
                        "Test City",
                        "Test State",
                        "12345",
                        "Test Country",
                        BigDecimal.valueOf(10_000)));

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("User registered successfully")));
    }

    /** Verifies that anonymous registration succeeds without a CSRF token. */
    @Test
    @WithAnonymousUser
    void shouldAllowRegistrationWithoutCsrfToken() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                TEST_USERNAME,
                TEST_EMAIL,
                "Test",
                "User",
                TEST_PASSWORD,
                9848022334L,
                "junitAddress",
                null,
                "Test City",
                "Test State",
                "12345",
                "Test Country");
        doNothing().when(registrationService).registerUser(any(RegistrationRequest.class));
        // Mock CustomerServiceClient to return a valid CustomerResponse
        when(customerServiceClient.getOrCreateCustomer(any(CustomerRequest.class)))
                .thenReturn(new CustomerResponse(
                        1L,
                        TEST_USERNAME,
                        TEST_EMAIL,
                        "9848022334",
                        "junitAddress",
                        null,
                        "Test City",
                        "Test State",
                        "12345",
                        "Test Country",
                        BigDecimal.valueOf(10_000)));

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully"));
    }

    /** Verifies that invalid registration fields produce HTTP 400. */
    @Test
    @WithAnonymousUser
    void shouldReturn400WhenRegistrationDataIsInvalid() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                "", // invalid username
                "invalid-email", // invalid email
                "", // invalid firstName
                "", // invalid lastName
                "pwd", // valid password
                9848022334L,
                "junitAddress",
                null,
                "Test City",
                "Test State",
                "12345",
                "Test Country");

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("invalidAddressComponents")
    void shouldRejectMissingOrBlankAddressComponent(String field, String value) throws Exception {
        Map<String, Object> request = new HashMap<>(Map.of(
                "username", TEST_USERNAME,
                "email", TEST_EMAIL,
                "firstName", "Test",
                "lastName", "User",
                "password", TEST_PASSWORD,
                "addressLine1", "123 Main Street",
                "city", "Test City",
                "state", "Test State",
                "zipCode", "12345",
                "country", "Test Country"));
        if ("missing".equals(value)) {
            request.remove(field);
        } else {
            request.put(field, value);
        }

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(registrationService, customerServiceClient);
    }

    private static Stream<Arguments> invalidAddressComponents() {
        return Stream.of("addressLine1", "city", "state", "zipCode", "country")
                .flatMap(field -> Stream.of("missing", null, "", " \t\n").map(value -> Arguments.of(field, value)));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409})
    void shouldCompensateConfirmedCustomerRejectionAndPropagateOriginalException(int status) {
        var failure = new HttpClientErrorException(HttpStatus.valueOf(status));
        when(customerServiceClient.getOrCreateCustomer(any(CustomerRequest.class)))
                .thenThrow(failure);

        var controller = new RegistrationController(registrationService, customerServiceClient);
        assertThatThrownBy(() -> controller.register(validRegistrationRequest()))
                .isSameAs(failure);

        verify(registrationService).registerUser(any(RegistrationRequest.class));
        verify(registrationService).deleteUserByUsername(TEST_USERNAME);
    }

    @ParameterizedTest
    @MethodSource("indeterminateCustomerFailures")
    void shouldPreserveKeycloakUserAndPropagateOriginalException(RuntimeException failure) {
        when(customerServiceClient.getOrCreateCustomer(any(CustomerRequest.class)))
                .thenThrow(failure);

        var controller = new RegistrationController(registrationService, customerServiceClient);
        assertThatThrownBy(() -> controller.register(validRegistrationRequest()))
                .isSameAs(failure);

        verify(registrationService).registerUser(any(RegistrationRequest.class));
        verify(registrationService, never()).deleteUserByUsername(any());
    }

    private static Stream<RuntimeException> indeterminateCustomerFailures() {
        return Stream.of(
                new ResourceAccessException("Response timed out", new SocketTimeoutException("Read timed out")),
                new HttpClientErrorException(HttpStatus.REQUEST_TIMEOUT),
                new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR),
                new HttpServerErrorException(HttpStatus.BAD_GATEWAY),
                new HttpServerErrorException(HttpStatus.GATEWAY_TIMEOUT),
                new RestClientException("Cannot decode customer response"),
                new IllegalStateException("Unexpected client failure"));
    }

    private static RegistrationRequest validRegistrationRequest() {
        return new RegistrationRequest(
                TEST_USERNAME,
                TEST_EMAIL,
                "Test",
                "User",
                TEST_PASSWORD,
                9848022334L,
                "junitAddress",
                null,
                "Test City",
                "Test State",
                "12345",
                "Test Country");
    }

    /** Verifies that a Keycloak registration failure produces HTTP 500. */
    @Test
    @WithAnonymousUser
    void shouldReturn500WhenKeycloakRegistrationFails() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                TEST_USERNAME,
                TEST_EMAIL,
                "Test",
                "User",
                TEST_PASSWORD,
                9848022334L,
                "junitAddress",
                null,
                "Test City",
                "Test State",
                "12345",
                "Test Country");

        doThrow(new KeyCloakException("500 Internal server Exception : Keycloak registration failed"))
                .when(registrationService)
                .registerUser(any(RegistrationRequest.class));

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", is("https://api.retailstore.com/errors/keycloak-registration")))
                .andExpect(jsonPath("$.title", is("Keycloak Registration Error")))
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.detail", is("500 Internal server Exception : Keycloak registration failed")))
                .andExpect(jsonPath("$.instance", is(REGISTER_ENDPOINT)));
    }
}
