package com.example.retailstore.webapp.web.controller;

import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.retailstore.webapp.clients.customer.CustomerRequest;
import com.example.retailstore.webapp.clients.customer.CustomerResponse;
import com.example.retailstore.webapp.clients.customer.CustomerServiceClient;
import com.example.retailstore.webapp.config.TestSecurityConfig;
import com.example.retailstore.webapp.exception.KeyCloakException;
import com.example.retailstore.webapp.services.KeycloakRegistrationService;
import com.example.retailstore.webapp.web.model.request.RegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(RegistrationController.class)
@Import({TestSecurityConfig.class})
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
    private KeycloakRegistrationService registrationService;

    @MockitoBean
    private CustomerServiceClient customerServiceClient;

    @Test
    @WithAnonymousUser
    void shouldRegisterUserSuccessfully() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                TEST_USERNAME, TEST_EMAIL, "Test", "User", TEST_PASSWORD, 9848022334L, "junitAddress");
        doNothing().when(registrationService).registerUser(any(RegistrationRequest.class));
        // Mock CustomerServiceClient to return a valid CustomerResponse
        when(customerServiceClient.getOrCreateCustomer(any(CustomerRequest.class)))
                .thenReturn(new CustomerResponse(1L, TEST_USERNAME, TEST_EMAIL, "9848022334", "junitAddress", 10_000));

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("User registered successfully")));
    }

    @Test
    @WithAnonymousUser
    void shouldAllowRegistrationWithoutCsrfToken() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                TEST_USERNAME, TEST_EMAIL, "Test", "User", TEST_PASSWORD, 9848022334L, "junitAddress");
        doNothing().when(registrationService).registerUser(any(RegistrationRequest.class));
        // Mock CustomerServiceClient to return a valid CustomerResponse
        when(customerServiceClient.getOrCreateCustomer(any(CustomerRequest.class)))
                .thenReturn(new CustomerResponse(1L, TEST_USERNAME, TEST_EMAIL, "9848022334", "junitAddress", 0));

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully"));
    }

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
                "junitAddress");

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void shouldReturn500WhenKeycloakRegistrationFails() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                TEST_USERNAME, TEST_EMAIL, "Test", "User", TEST_PASSWORD, 9848022334L, "junitAddress");

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
