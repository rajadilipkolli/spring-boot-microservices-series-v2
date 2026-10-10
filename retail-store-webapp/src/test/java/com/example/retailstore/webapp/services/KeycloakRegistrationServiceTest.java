package com.example.retailstore.webapp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.retailstore.webapp.config.KeycloakProperties;
import com.example.retailstore.webapp.exception.KeyCloakException;
import com.example.retailstore.webapp.exception.UserAlreadyExistsException;
import com.example.retailstore.webapp.model.request.RegistrationRequest;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.RoleRepresentation;
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
    private UserResource userResource;

    @Mock
    private RolesResource rolesResource;

    @Mock
    private RoleResource roleResource;

    @Mock
    private RoleMappingResource roleMappingResource;

    @Mock
    private RoleScopeResource realmRoleScope;

    @Mock
    private Response response;

    @Captor
    ArgumentCaptor<UserRepresentation> userCaptor;

    private KeycloakRegistrationService svc;

    /** Creates the service with a mocked admin client and the retailstore realm. */
    @BeforeEach
    void beforeEach() {
        given(keycloakProperties.getRealm()).willReturn("retailstore");
        svc = new KeycloakRegistrationService(keycloakProperties, keycloak);
    }

    /** Verifies user details, permanent credentials, role assignment, and response cleanup. */
    @Test
    void registerSuccessPathCreatesUserAndAssignsUserRole() {
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
        given(response.getStatusInfo()).willReturn(Response.Status.CREATED);
        given(response.getLocation())
                .willReturn(URI.create("http://localhost/realms/retailstore/users/created-user-id"));
        given(usersResource.get("created-user-id")).willReturn(userResource);
        given(userResource.roles()).willReturn(roleMappingResource);
        given(roleMappingResource.realmLevel()).willReturn(realmRoleScope);
        given(realmResource.roles()).willReturn(rolesResource);
        given(rolesResource.get("user")).willReturn(roleResource);
        RoleRepresentation userRole = new RoleRepresentation();
        userRole.setId("user-role-id");
        userRole.setName("user");
        given(roleResource.toRepresentation()).willReturn(userRole);

        assertDoesNotThrow(() -> svc.registerUser(request));

        verify(usersResource).create(userCaptor.capture());
        UserRepresentation captured = userCaptor.getValue();

        assertThat(captured.getUsername()).isEqualTo("testuser");
        assertThat(captured.getEmail()).isEqualTo("test@example.com");
        assertThat(captured.getFirstName()).isEqualTo("Test");
        assertThat(captured.getLastName()).isEqualTo("User");
        assertThat(captured.isEnabled()).isTrue();
        assertThat(captured.getRealmRoles()).isNull();
        verify(realmRoleScope).add(List.of(userRole));
        verify(response).close();
        assertThat(captured.getCredentials()).hasSize(1);
        assertThat(captured.getCredentials().get(0).getValue()).isEqualTo("pass123");
        assertThat(captured.getCredentials().get(0).isTemporary()).isFalse();
    }

    /** Verifies HTTP 409 raises a duplicate-user error, skips roles, and closes the response. */
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
        verifyNoInteractions(userResource, realmRoleScope);
        verify(response).close();
    }

    /** Verifies HTTP 500 raises a registration error, skips roles, and closes the response. */
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
        verifyNoInteractions(userResource, realmRoleScope);
        verify(response).close();
    }
}
