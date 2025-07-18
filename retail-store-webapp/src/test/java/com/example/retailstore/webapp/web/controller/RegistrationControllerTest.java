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

import com.example.retailstore.webapp.clients.customer.CustomerServiceClient;
import com.example.retailstore.webapp.config.TestSecurityConfig;
import com.example.retailstore.webapp.exception.KeyCloakException;
import com.example.retailstore.webapp.services.KeycloakRegistrationService;
import com.example.retailstore.webapp.web.model.request.RegistrationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
    private ObjectMapper objectMapper;

    @MockitoBean
    private KeycloakRegistrationService registrationService;

    @MockitoBean
    private CustomerServiceClient customerServiceClient;

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                TEST_USERNAME, TEST_EMAIL, "Test", "User", TEST_PASSWORD, 9848022334L, "junitAddress");
        doNothing().when(registrationService).registerUser(any(RegistrationRequest.class));
        // Mock CustomerServiceClient to return a valid CustomerResponse
        when(customerServiceClient.getOrCreateCustomer(
                        any(com.example.retailstore.webapp.clients.customer.CustomerRequest.class)))
                .thenReturn(new com.example.retailstore.webapp.clients.customer.CustomerResponse(
                        1L, TEST_USERNAME, TEST_EMAIL, "9848022334", "junitAddress", 0));

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully"));
    }

    @Test
    void shouldAllowRegistrationWithoutCsrfToken() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                TEST_USERNAME, TEST_EMAIL, "Test", "User", TEST_PASSWORD, 9848022334L, "junitAddress");
        doNothing().when(registrationService).registerUser(any(RegistrationRequest.class));
        // Mock CustomerServiceClient to return a valid CustomerResponse
        when(customerServiceClient.getOrCreateCustomer(
                        any(com.example.retailstore.webapp.clients.customer.CustomerRequest.class)))
                .thenReturn(new com.example.retailstore.webapp.clients.customer.CustomerResponse(
                        1L, TEST_USERNAME, TEST_EMAIL, "9848022334", "junitAddress", 0));

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully"));
    }

    @Test
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
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn500WhenKeycloakRegistrationFails() throws Exception {
        RegistrationRequest request = new RegistrationRequest(
                TEST_USERNAME, TEST_EMAIL, "Test", "User", TEST_PASSWORD, 9848022334L, "junitAddress");

        doThrow(new KeyCloakException("500 Internal server Exception : Keycloak registration failed"))
                .when(registrationService)
                .registerUser(any(RegistrationRequest.class));

        mockMvc.perform(post(REGISTER_ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type", is("https://api.retailstore.com/errors/keycloak-registration")))
                .andExpect(jsonPath("$.title", is("Keycloak Registration Error")))
                .andExpect(jsonPath("$.status", is(500)))
                .andExpect(jsonPath("$.detail", is("500 Internal server Exception : Keycloak registration failed")))
                .andExpect(jsonPath("$.instance", is(REGISTER_ENDPOINT)));
    }
}
