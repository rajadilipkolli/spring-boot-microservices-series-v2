package com.example.retailstore.webapp.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.retailstore.webapp.config.KeycloakProperties;
import com.example.retailstore.webapp.web.model.request.RegistrationRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class KeycloakRegistrationServiceTest {

    @Mock
    private RestClient restClient;

    @Mock
    private KeycloakProperties keycloakProperties;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Captor
    private ArgumentCaptor<Map<String, Object>> requestCaptor; // Changed to capture Map<String, Object>

    @Captor
    private ArgumentCaptor<String> stringRequestCaptor; // Added for capturing String body

    private KeycloakRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new KeycloakRegistrationService("http://localhost:9191", restClient, keycloakProperties);
        lenient().when(restClient.post()).thenReturn(requestBodyUriSpec);
        lenient().when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.contentType(any(MediaType.class))).thenReturn(requestBodySpec);
        // Make stubbings for body lenient and more specific if possible
        lenient().when(requestBodySpec.body(anyString())).thenReturn(requestBodySpec); // For token call
        lenient().when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec); // For user creation call
        lenient()
                .when(requestBodySpec.body(any(MultiValueMap.class)))
                .thenReturn(requestBodySpec); // Keep for other potential uses
        lenient().when(requestBodySpec.header(any(), any())).thenReturn(requestBodySpec);
        lenient().when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void shouldRegisterUserSuccessfully() {
        // Given
        var request = new RegistrationRequest(
                "testuser", "test@example.com", "Test", "User", "password123", 9848022334L, "junitAddress");

        when(responseSpec.body(eq(Map.class))).thenReturn(Map.of("access_token", "mock-token"));
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());
        when(keycloakProperties.getRealm()).thenReturn("test-realm");

        // When/Then
        assertDoesNotThrow(() -> registrationService.registerUser(request));
        verify(requestBodySpec).header(eq("Authorization"), eq("Bearer mock-token"));
    }

    @Test
    @Disabled
    void shouldRegisterUserWithCorrectRoles() {
        // Arrange
        RegistrationRequest registrationRequest = new RegistrationRequest(
                "testUser", "test@example.com", "Test", "User", "testPassword", 9848022334L, "junitAddress");

        // Mock Keycloak responses
        when(keycloakProperties.getRealm()).thenReturn("test-realm");
        when(keycloakProperties.getAdminClientId()).thenReturn("admin-cli");
        when(keycloakProperties.getAdminClientSecret()).thenReturn("admin-secret");

        // Mock specific responses for token and user creation calls
        when(responseSpec.body(eq(Map.class))).thenReturn(Map.of("access_token", "mock-token")); // For token response
        when(responseSpec.toBodilessEntity())
                .thenReturn(ResponseEntity.status(201).build()); // For user creation response

        // Act
        registrationService.registerUser(registrationRequest);

        // Assert
        verify(restClient, times(2)).post(); // Token endpoint and user creation endpoint

        // Capture arguments for body calls specifically by type
        verify(requestBodySpec).body(stringRequestCaptor.capture()); // Captures String body for token
        verify(requestBodySpec).body(requestCaptor.capture()); // Captures Map body for user creation

        String tokenRequestBody = stringRequestCaptor.getValue();
        assertTrue(tokenRequestBody.contains("grant_type=password"));
        assertTrue(tokenRequestBody.contains("client_id=" + keycloakProperties.getAdminClientId()));
        assertTrue(tokenRequestBody.contains("client_secret=" + keycloakProperties.getAdminClientSecret()));
        assertTrue(tokenRequestBody.contains("username=admin"));
        assertTrue(tokenRequestBody.contains("password=admin1234"));

        // Second call is for user creation
        Map<String, Object> userRequestBody = requestCaptor.getValue(); // No explicit cast needed if captor is typed
        assertEquals(registrationRequest.username(), userRequestBody.get("username"));
        assertEquals(registrationRequest.email(), userRequestBody.get("email"));
        assertEquals(registrationRequest.firstName(), userRequestBody.get("firstName"));
        assertEquals(registrationRequest.lastName(), userRequestBody.get("lastName"));
        assertTrue((Boolean) userRequestBody.get("enabled"));

        @SuppressWarnings("unchecked") // Suppress warning for casting Object to List<Map<String,String>>
        List<Map<String, String>> credentials = (List<Map<String, String>>) userRequestBody.get("credentials");
        assertEquals(1, credentials.size());
        assertEquals("password", credentials.get(0).get("type"));
        assertEquals(registrationRequest.password(), credentials.get(0).get("value"));
        assertFalse(Boolean.parseBoolean((String) credentials.get(0).get("temporary")));

        @SuppressWarnings("unchecked") // Suppress warning for casting Object to List
        List<String> realmRoles = (List<String>) userRequestBody.get("realmRoles");
        assertTrue(realmRoles.contains("user"));
    }

    @Test
    void shouldThrowExceptionWhenAdminTokenCannotBeObtained() {
        // Given
        var request = new RegistrationRequest(
                "testuser", "test@example.com", "Test", "User", "password123", 9848022334L, "junitAddress");
        // keycloakProperties.getRealm() is NOT called in the getAdminToken() path, only for user creation.
        // So, this stubbing is unnecessary if token retrieval fails before user creation.
        // If it IS called, it should be part of the setup for the specific path being tested.
        // For this test, the failure happens during getAdminToken, so getRealm() for user creation URI is not reached.
        // lenient().when(keycloakProperties.getRealm()).thenReturn("test-realm"); // Removed as it's not used in this
        // failure path
        when(keycloakProperties.getAdminClientId()).thenReturn("admin-cli");
        when(keycloakProperties.getAdminClientSecret()).thenReturn("admin-secret");

        // Simulate failure in retrieving token
        when(responseSpec.body(eq(Map.class))).thenReturn(Map.of("error", "unauthorized"));

        // When/Then
        assertThrows(RuntimeException.class, () -> registrationService.registerUser(request));
        // Verify that getRealm was NOT called, confirming it's not part of the failing token path.
        verify(keycloakProperties, never()).getRealm();
    }

    @Test
    void shouldThrowExceptionWhenKeycloakRegistrationFails() {
        // Given
        var request = new RegistrationRequest(
                "testuser", "test@example.com", "Test", "User", "password123", 9848022334L, "junitAddress");
        when(keycloakProperties.getRealm()).thenReturn("test-realm");
        when(keycloakProperties.getAdminClientId()).thenReturn("admin-cli");
        when(keycloakProperties.getAdminClientSecret()).thenReturn("admin-secret");

        when(responseSpec.body(eq(Map.class))).thenReturn(Map.of("access_token", "mock-token"));
        when(responseSpec.toBodilessEntity()).thenThrow(new RuntimeException("Keycloak registration failed"));

        // When/Then
        assertThrows(RuntimeException.class, () -> registrationService.registerUser(request));
    }
}
