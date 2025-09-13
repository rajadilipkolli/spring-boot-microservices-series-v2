package com.example.retailstore.webapp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.retailstore.webapp.config.KeycloakProperties;
import com.example.retailstore.webapp.exception.KeyCloakException;
import com.example.retailstore.webapp.web.model.request.RegistrationRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    private KeycloakRegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService = new KeycloakRegistrationService("http://localhost:9191", restClient, keycloakProperties);
        given(restClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(any(String.class))).willReturn(requestBodySpec);
        given(requestBodySpec.contentType(any(MediaType.class))).willReturn(requestBodySpec);
        // Generic body stubbing to cover all overloads; use doAnswer to avoid overload/type matching issues
        doAnswer(invocation -> requestBodySpec).when(requestBodySpec).body(any());
        // Also stub common overloaded signatures explicitly
        doAnswer(invocation -> requestBodySpec).when(requestBodySpec).body(anyString());
        doAnswer(invocation -> requestBodySpec).when(requestBodySpec).body(any(java.util.Map.class));
        doAnswer(invocation -> requestBodySpec).when(requestBodySpec).body(any(MultiValueMap.class));
        given(requestBodySpec.header(any(), any())).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve()).willReturn(responseSpec);
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
        verify(requestBodySpec, atLeastOnce()).contentType(eq(MediaType.APPLICATION_FORM_URLENCODED));
        verify(requestBodySpec).header(eq("Authorization"), eq("Bearer mock-token"));
    }

    @Test
    void shouldRegisterUserWithCorrectRoles() {
        // Arrange
        RegistrationRequest registrationRequest = new RegistrationRequest(
                "testUser", "test@example.com", "Test", "User", "testPassword", 9848022334L, "junitAddress");

        // Mock Keycloak responses
        given(keycloakProperties.getRealm()).willReturn("test-realm");
        given(keycloakProperties.getAdminClientId()).willReturn("admin-cli");
        given(keycloakProperties.getAdminClientSecret()).willReturn("admin-secret");
        // Admin credentials used when obtaining admin token
        given(keycloakProperties.getAdminUsername()).willReturn("admin");
        given(keycloakProperties.getAdminPassword()).willReturn("admin1234");

        // Mock specific responses for token and user creation calls
        given(responseSpec.body(any(ParameterizedTypeReference.class)))
                .willReturn(Map.of("access_token", "mock-token")); // For token response
        given(responseSpec.toBodilessEntity())
                .willReturn(ResponseEntity.status(201).build()); // For user creation response

        // Act
        registrationService.registerUser(registrationRequest);

        // Assert
        verify(restClient, times(2)).post(); // Token endpoint and user creation endpoint

        // Capture both body invocations (token form and user creation JSON)
        verify(requestBodySpec, times(2)).body(requestCaptor.capture());

        var allBodies = requestCaptor.getAllValues();
        // First call is the token form (LinkedMultiValueMap) - values may be lists
        Map<?, ?> tokenBody = allBodies.getFirst();
        Object grant = tokenBody.get("grant_type");
        if (grant instanceof List) {
            assertThat(((List<?>) grant).getFirst()).isEqualTo("password");
        } else {
            assertThat(grant).isEqualTo("password");
        }
        Object clientId = tokenBody.get("client_id");
        if (clientId instanceof List) {
            assertThat(((List<?>) clientId).getFirst()).isEqualTo(keycloakProperties.getAdminClientId());
        } else {
            assertThat(clientId).isEqualTo(keycloakProperties.getAdminClientId());
        }
        Object clientSecret = tokenBody.get("client_secret");
        if (clientSecret instanceof List) {
            assertThat(((List<?>) clientSecret).getFirst()).isEqualTo(keycloakProperties.getAdminClientSecret());
        } else {
            assertThat(clientSecret).isEqualTo(keycloakProperties.getAdminClientSecret());
        }
        Object username = tokenBody.get("username");
        if (username instanceof List) {
            assertThat(((List<?>) username).getFirst()).isEqualTo("admin");
        } else {
            assertThat(username).isEqualTo("admin");
        }
        Object password = tokenBody.get("password");
        if (password instanceof List) {
            assertThat(((List<?>) password).getFirst()).isEqualTo("admin1234");
        } else {
            assertThat(password).isEqualTo("admin1234");
        }

        // Second call is for user creation
        Map<String, Object> userRequestBody = (Map<String, Object>) allBodies.get(1);
        assertThat(userRequestBody.get("username")).isEqualTo(registrationRequest.username());
        // Password is stored in credentials, not as a top-level property
        assertThat(userRequestBody.get("email")).isEqualTo(registrationRequest.email());
        assertThat(userRequestBody.get("firstName")).isEqualTo(registrationRequest.firstName());
        assertThat(userRequestBody.get("lastName")).isEqualTo(registrationRequest.lastName());
        assertThat((Boolean) userRequestBody.get("enabled")).isTrue();

        @SuppressWarnings("unchecked") // Suppress warning for casting Object to List<Map<String,Object>>
        List<Map<String, Object>> credentials = (List<Map<String, Object>>) userRequestBody.get("credentials");
        assertThat(credentials).isNotEmpty().hasSize(1);
        assertThat(credentials.getFirst().get("type")).isEqualTo("password");
        assertThat(credentials.getFirst().get("value")).isEqualTo(registrationRequest.password());
        Object tempVal = credentials.getFirst().get("temporary");
        if (tempVal instanceof Boolean) {
            assertThat((Boolean) tempVal).isFalse();
        } else {
            assertThat(Boolean.parseBoolean(String.valueOf(tempVal))).isFalse();
        }

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
        // given(keycloakProperties.getRealm()).willReturn("test-realm"); // Removed as it's not used in this
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
        assertThatThrownBy(() -> registrationService.registerUser(request))
                .isInstanceOf(KeyCloakException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("registration");
    }
}
