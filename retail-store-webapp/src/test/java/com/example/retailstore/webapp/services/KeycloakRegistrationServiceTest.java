package com.example.retailstore.webapp.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.retailstore.webapp.web.model.request.RegistrationRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class KeycloakRegistrationServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private KeycloakRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new KeycloakRegistrationService("http://localhost:9191", restClient);
        lenient().when(restClient.post()).thenReturn(requestBodyUriSpec);
        lenient().when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.body(any(String.class))).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void shouldRegisterUserSuccessfully() {
        // Given
        var request = new RegistrationRequest("testuser", "test@example.com", "Test", "User", "password123");

        when(responseSpec.body(eq(Map.class))).thenReturn(Map.of("access_token", "mock-token"));
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        // When/Then
        assertDoesNotThrow(() -> registrationService.registerUser(request));
        verify(requestBodySpec).header(eq("Authorization"), eq("Bearer mock-token"));
    }

    @Test
    void shouldRegisterUserWithCorrectRoles() {
        // Given
        var request = new RegistrationRequest("testuser", "test@example.com", "Test", "User", "password123");

        when(responseSpec.body(eq(Map.class))).thenReturn(Map.of("access_token", "mock-token"));
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        // When
        registrationService.registerUser(request);

        // Then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> requestCaptor = ArgumentCaptor.forClass(Map.class);
        verify(requestBodySpec).body(requestCaptor.capture());

        Map<String, Object> requestBody = requestCaptor.getValue();
        assertTrue(requestBody.containsKey("realmRoles"));
        var roles = (java.util.List<?>) requestBody.get("realmRoles");
        assertTrue(roles.contains("USER"));
    }

    @Test
    void shouldThrowExceptionWhenAdminTokenCannotBeObtained() {
        // Given
        var request = new RegistrationRequest("testuser", "test@example.com", "Test", "User", "password123");

        when(responseSpec.body(eq(Map.class))).thenReturn(Map.of("error", "unauthorized"));

        // When/Then
        assertThrows(RuntimeException.class, () -> registrationService.registerUser(request));
    }

    @Test
    void shouldThrowExceptionWhenKeycloakRegistrationFails() {
        // Given
        var request = new RegistrationRequest("testuser", "test@example.com", "Test", "User", "password123");

        when(responseSpec.body(eq(Map.class))).thenReturn(Map.of("access_token", "mock-token"));
        when(responseSpec.toBodilessEntity()).thenThrow(new RuntimeException("Keycloak registration failed"));

        // When/Then
        assertThrows(RuntimeException.class, () -> registrationService.registerUser(request));
    }
}
