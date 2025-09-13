package com.example.retailstore.webapp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import org.springframework.core.ParameterizedTypeReference;
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

        given(responseSpec.body(any(ParameterizedTypeReference.class)))
                .willReturn(Map.of("access_token", "mock-token"));
        given(responseSpec.toBodilessEntity()).willReturn(ResponseEntity.ok().build());
        given(keycloakProperties.getRealm()).willReturn("test-realm");

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
        given(keycloakProperties.getRealm()).willReturn("test-realm");
        given(keycloakProperties.getAdminClientId()).willReturn("admin-cli");
        given(keycloakProperties.getAdminClientSecret()).willReturn("admin-secret");

        // Mock specific responses for token and user creation calls
        given(responseSpec.body(any(ParameterizedTypeReference.class)))
                .willReturn(Map.of("access_token", "mock-token")); // For token response
        given(responseSpec.toBodilessEntity())
                .willReturn(ResponseEntity.status(201).build()); // For user creation response

        // Act
        registrationService.registerUser(registrationRequest);

        // Assert
        verify(restClient, times(2)).post(); // Token endpoint and user creation endpoint

        // Capture arguments for body calls specifically by type
        verify(requestBodySpec).body(stringRequestCaptor.capture()); // Captures String body for token
        verify(requestBodySpec).body(requestCaptor.capture()); // Captures Map body for user creation

        String tokenRequestBody = stringRequestCaptor.getValue();
        assertThat(tokenRequestBody.contains("grant_type=password")).isTrue();
        assertThat(tokenRequestBody.contains("client_id=" + keycloakProperties.getAdminClientId()))
                .isTrue();
        assertThat(tokenRequestBody.contains("client_secret=" + keycloakProperties.getAdminClientSecret()))
                .isTrue();
        assertThat(tokenRequestBody.contains("username=admin")).isTrue();
        assertThat(tokenRequestBody.contains("password=admin1234")).isTrue();

        // Second call is for user creation
        Map<String, Object> userRequestBody = requestCaptor.getValue(); // No explicit cast needed if captor is typed
        assertThat(userRequestBody.get("username")).isEqualTo(registrationRequest.username());
        assertThat(userRequestBody.get("password")).isEqualTo(registrationRequest.password());
        assertThat(userRequestBody.get("email")).isEqualTo(registrationRequest.email());
        assertThat(userRequestBody.get("firstName")).isEqualTo(registrationRequest.firstName());
        assertThat(userRequestBody.get("lastName")).isEqualTo(registrationRequest.lastName());
        assertThat((Boolean) userRequestBody.get("enabled")).isTrue();

        @SuppressWarnings("unchecked") // Suppress warning for casting Object to List<Map<String,String>>
        List<Map<String, String>> credentials = (List<Map<String, String>>) userRequestBody.get("credentials");
        assertThat(credentials).isNotEmpty().hasSize(1);
        assertThat(credentials.getFirst().get("type")).isEqualTo("password");
        assertThat(credentials.getFirst().get("value")).isEqualTo(registrationRequest.password());
        assertThat(Boolean.parseBoolean(credentials.getFirst().get("temporary")))
                .isFalse();

        @SuppressWarnings("unchecked") // Suppress warning for casting Object to List
        List<String> realmRoles = (List<String>) userRequestBody.get("realmRoles");
        assertThat(realmRoles.contains("user")).isTrue();
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
        given(keycloakProperties.getAdminClientId()).willReturn("admin-cli");
        given(keycloakProperties.getAdminClientSecret()).willReturn("admin-secret");

        // Simulate failure in retrieving token
        given(responseSpec.body(any(ParameterizedTypeReference.class))).willReturn(Map.of("error", "unauthorized"));

        // When/Then
        assertThatThrownBy(() -> registrationService.registerUser(request)).isInstanceOf(RuntimeException.class);
        // Verify that getRealm was NOT called, confirming it's not part of the failing token path.
        verify(keycloakProperties, never()).getRealm();
    }

    @Test
    void shouldThrowExceptionWhenKeycloakRegistrationFails() {
        // Given
        var request = new RegistrationRequest(
                "testuser", "test@example.com", "Test", "User", "password123", 9848022334L, "junitAddress");
        given(keycloakProperties.getRealm()).willReturn("test-realm");
        given(keycloakProperties.getAdminClientId()).willReturn("admin-cli");
        given(keycloakProperties.getAdminClientSecret()).willReturn("admin-secret");

        given(responseSpec.body(any(ParameterizedTypeReference.class)))
                .willReturn(Map.of("access_token", "mock-token"));
        given(responseSpec.toBodilessEntity()).willThrow(new RuntimeException("Keycloak registration failed"));

        // When/Then
        assertThatThrownBy(() -> registrationService.registerUser(request)).isInstanceOf(RuntimeException.class);
    }
}
