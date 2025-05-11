package com.example.retailstore.webapp.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.retailstore.webapp.config.KeycloakTestContainer;
import com.example.retailstore.webapp.config.TestSecurityConfig;
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
@Import({TestSecurityConfig.class, KeycloakTestContainer.class})
class RegistrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KeycloakRegistrationService registrationService;

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        RegistrationRequest request =
                new RegistrationRequest("testuser", "test@example.com", "Test", "User", "password123");
        doNothing().when(registrationService).registerUser(any(RegistrationRequest.class));

        mockMvc.perform(post("/api/register")
                        .with(csrf())
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
                "pwd" // valid password
                );

        mockMvc.perform(post("/api/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenKeycloakRegistrationFails() throws Exception {
        RegistrationRequest request =
                new RegistrationRequest("testuser", "test@example.com", "Test", "User", "password123");

        doThrow(new RuntimeException("Keycloak registration failed"))
                .when(registrationService)
                .registerUser(any(RegistrationRequest.class));

        mockMvc.perform(post("/api/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Keycloak registration failed"));
    }
}
