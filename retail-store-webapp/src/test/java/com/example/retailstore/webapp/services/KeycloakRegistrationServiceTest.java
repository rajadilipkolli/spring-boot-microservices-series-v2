package com.example.retailstore.webapp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.example.retailstore.webapp.config.KeycloakProperties;
import com.example.retailstore.webapp.exception.KeyCloakException;
import com.example.retailstore.webapp.web.model.request.RegistrationRequest;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
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
    ArgumentCaptor<MultiValueMap<String, String>> formCaptor;

    @Captor
    ArgumentCaptor<Map<String, Object>> jsonCaptor;

    private KeycloakRegistrationService svc;

    @BeforeEach
    void beforeEach() {
        svc = new KeycloakRegistrationService("http://kc", restClient, keycloakProperties);
        given(restClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri(anyString())).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve()).willReturn(responseSpec);
    }

    @Test
    void registerSuccessPathPostsTokenAndUser() {
        // arrange
        given(keycloakProperties.getAdminClientId()).willReturn("admin-cli");
        given(keycloakProperties.getAdminClientSecret()).willReturn("secret");
        given(keycloakProperties.getAdminUsername()).willReturn("admin");
        given(keycloakProperties.getAdminPassword()).willReturn("admin123");
        given(keycloakProperties.getRealm()).willReturn("realm1");

        // token form body: accept MultiValueMap
        given(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .willReturn(requestBodySpec);
        given(requestBodySpec.body(ArgumentMatchers.<MultiValueMap<String, String>>any()))
                .willReturn(requestBodySpec);
        given(responseSpec.body(ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .willReturn(Map.of("access_token", "tkn"));

        // user creation JSON
        given(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).willReturn(requestBodySpec);
        given(requestBodySpec.body(ArgumentMatchers.<Map<String, Object>>any())).willReturn(requestBodySpec);
        given(requestBodySpec.header(anyString(), any())).willReturn(requestBodySpec);
        given(responseSpec.toBodilessEntity()).willReturn(ResponseEntity.ok().build());

        var r = new RegistrationRequest("u1", "e@example.com", "First", "Last", "p", 1L, "addr");

        // act / assert
        assertDoesNotThrow(() -> svc.registerUser(r));

        // verify: capture the two body calls in the execution order (form then json)
        InOrder ord = inOrder(requestBodySpec, responseSpec);
        ord.verify(requestBodySpec).contentType(MediaType.APPLICATION_FORM_URLENCODED);
        ord.verify(requestBodySpec).body(formCaptor.capture());
        // assert token form contains expected keys; values can be any
        var tokenForm = formCaptor.getValue();
        assertThat(tokenForm.keySet())
                .containsAll(Set.of("client_id", "client_secret", "username", "password", "grant_type"));

        ord.verify(requestBodySpec).contentType(MediaType.APPLICATION_JSON);
        ord.verify(requestBodySpec).header(eq(HttpHeaders.AUTHORIZATION), eq("Bearer tkn"));
        ord.verify(requestBodySpec).body(jsonCaptor.capture());
        var userJson = jsonCaptor.getValue();
        assertThat(userJson.keySet()
                .containsAll(Set.of("username", "email", "firstName", "lastName", "enabled", "credentials")));
        ord.verify(responseSpec).toBodilessEntity();
    }

    @Test
    void registerFailsWhenTokenReturnsErrorMap() {
        given(keycloakProperties.getAdminClientId()).willReturn("admin-cli");
        given(keycloakProperties.getAdminClientSecret()).willReturn("secret");
        given(keycloakProperties.getAdminUsername()).willReturn("admin");
        given(keycloakProperties.getAdminPassword()).willReturn("admin123");

        given(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .willReturn(requestBodySpec);
        given(requestBodySpec.body(ArgumentMatchers.<MultiValueMap<String, String>>any()))
                .willReturn(requestBodySpec);
        given(responseSpec.body(ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .willReturn(Map.of("error", "nope"));

        var r = new RegistrationRequest("u1", "e@example.com", "F", "L", "p", 2L, "addr");

        assertThatThrownBy(() -> svc.registerUser(r)).isInstanceOf(KeyCloakException.class);
    }

    @Test
    void registerFailsWhenTokenBodyIsNull() {
        given(keycloakProperties.getAdminClientId()).willReturn("admin-cli");
        given(keycloakProperties.getAdminClientSecret()).willReturn("secret");
        given(keycloakProperties.getAdminUsername()).willReturn("admin");
        given(keycloakProperties.getAdminPassword()).willReturn("admin123");

        given(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .willReturn(requestBodySpec);
        given(requestBodySpec.body(ArgumentMatchers.<MultiValueMap<String, String>>any()))
                .willReturn(requestBodySpec);
        given(responseSpec.body(ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .willReturn(null);

        var r = new RegistrationRequest("u1", "e@example.com", "F", "L", "p", 2L, "addr");
        assertThatThrownBy(() -> svc.registerUser(r)).isInstanceOf(KeyCloakException.class);
    }

    @Test
    void registerWrapsExceptionsFromTokenCall() {
        given(keycloakProperties.getAdminClientId()).willReturn("admin-cli");
        given(keycloakProperties.getAdminClientSecret()).willReturn("secret");
        given(keycloakProperties.getAdminUsername()).willReturn("admin");
        given(keycloakProperties.getAdminPassword()).willReturn("admin123");

        given(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .willReturn(requestBodySpec);
        given(requestBodySpec.body(ArgumentMatchers.<MultiValueMap<String, String>>any()))
                .willReturn(requestBodySpec);
        given(responseSpec.body(ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .willThrow(new RuntimeException("boom"));

        var r = new RegistrationRequest("u1", "e@example.com", "F", "L", "p", 2L, "addr");

        assertThatThrownBy(() -> svc.registerUser(r))
                .isInstanceOf(KeyCloakException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void registerWrapsExceptionsFromUserCreation() {
        // token ok
        given(keycloakProperties.getAdminClientId()).willReturn("admin-cli");
        given(keycloakProperties.getAdminClientSecret()).willReturn("secret");
        given(keycloakProperties.getAdminUsername()).willReturn("admin");
        given(keycloakProperties.getAdminPassword()).willReturn("admin123");
        given(keycloakProperties.getRealm()).willReturn("realm1");

        given(requestBodySpec.contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .willReturn(requestBodySpec);
        given(requestBodySpec.body(ArgumentMatchers.<MultiValueMap<String, String>>any()))
                .willReturn(requestBodySpec);
        given(responseSpec.body(ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()))
                .willReturn(Map.of("access_token", "tkn"));

        // user creation stubbed to fail
        given(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).willReturn(requestBodySpec);
        given(requestBodySpec.body(ArgumentMatchers.<Map<String, Object>>any())).willReturn(requestBodySpec);
        given(requestBodySpec.header(anyString(), any())).willReturn(requestBodySpec);
        given(responseSpec.toBodilessEntity()).willThrow(new RuntimeException("create-fail"));

        var r = new RegistrationRequest("u1", "e@example.com", "F", "L", "p", 2L, "addr");
        assertThatThrownBy(() -> svc.registerUser(r)).isInstanceOf(KeyCloakException.class);
        verify(requestBodySpec).header(eq(HttpHeaders.AUTHORIZATION), eq("Bearer tkn"));
    }
}
