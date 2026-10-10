package com.example.retailstore.webapp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.example.retailstore.webapp.config.KeycloakProperties;
import com.example.retailstore.webapp.exception.KeyCloakException;
import com.example.retailstore.webapp.exception.UserAlreadyExistsException;
import com.example.retailstore.webapp.model.request.RegistrationRequest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KeycloakRegistrationServiceTest {

    @Mock
    private Keycloak keycloak;

    @Mock
    private KeycloakProperties keycloakProperties;

    @Mock
    private RealmResource realmResource;

    @Mock
    private UsersResource usersResource;

    @Mock
    private Response response;

    @Captor
    ArgumentCaptor<UserRepresentation> userCaptor;

    private KeycloakRegistrationService svc;

    @BeforeEach
    void beforeEach() {
        given(keycloakProperties.getRealm()).willReturn("retailstore");
        svc = new KeycloakRegistrationService(keycloakProperties, keycloak);
    }

    @Test
    void registerSuccessPathPostsTokenAndUser() {
        RegistrationRequest request = new RegistrationRequest(
                "testuser",
                "test@example.com",
                "Test",
                "User",
                "pass123",
                1234567890L,
                "Addr1",
                null,
                "City",
                "State",
                "12345",
                "Country");

        given(keycloak.realm("retailstore")).willReturn(realmResource);
        given(realmResource.users()).willReturn(usersResource);
        given(usersResource.create(any(UserRepresentation.class))).willReturn(response);
        given(response.getStatus()).willReturn(201);

        assertDoesNotThrow(() -> svc.registerUser(request));

        verify(usersResource).create(userCaptor.capture());
        UserRepresentation captured = userCaptor.getValue();

        assertThat(captured.getUsername()).isEqualTo("testuser");
        assertThat(captured.getEmail()).isEqualTo("test@example.com");
        assertThat(captured.getFirstName()).isEqualTo("Test");
        assertThat(captured.getLastName()).isEqualTo("User");
        assertThat(captured.isEnabled()).isTrue();
        assertThat(captured.getRealmRoles()).containsExactly("user");
        assertThat(captured.getCredentials()).hasSize(1);
        assertThat(captured.getCredentials().get(0).getValue()).isEqualTo("pass123");
        assertThat(captured.getCredentials().get(0).isTemporary()).isFalse();
    }

    @Test
    void throwsUserAlreadyExistsWhen409() {
        RegistrationRequest request = new RegistrationRequest(
                "testuser",
                "test@example.com",
                "Test",
                "User",
                "pass123",
                1234567890L,
                "Addr1",
                null,
                "City",
                "State",
                "12345",
                "Country");

        given(keycloak.realm("retailstore")).willReturn(realmResource);
        given(realmResource.users()).willReturn(usersResource);
        given(usersResource.create(any(UserRepresentation.class))).willReturn(response);
        given(response.getStatus()).willReturn(409);

        assertThatThrownBy(() -> svc.registerUser(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("already taken");
    }

    @Test
    void throwsKeyCloakExceptionWhenOtherError() {
        RegistrationRequest request = new RegistrationRequest(
                "testuser",
                "test@example.com",
                "Test",
                "User",
                "pass123",
                1234567890L,
                "Addr1",
                null,
                "City",
                "State",
                "12345",
                "Country");

        given(keycloak.realm("retailstore")).willReturn(realmResource);
        given(realmResource.users()).willReturn(usersResource);
        given(usersResource.create(any(UserRepresentation.class))).willReturn(response);
        given(response.getStatus()).willReturn(500);

        assertThatThrownBy(() -> svc.registerUser(request))
                .isInstanceOf(KeyCloakException.class)
                .hasMessageContaining("Failed to register user. Status: 500");
    }
}
