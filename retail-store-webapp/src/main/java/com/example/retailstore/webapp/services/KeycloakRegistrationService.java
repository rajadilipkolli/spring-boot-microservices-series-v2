package com.example.retailstore.webapp.services;

import com.example.retailstore.webapp.config.KeycloakProperties;
import com.example.retailstore.webapp.exception.KeyCloakException;
import com.example.retailstore.webapp.exception.UserAlreadyExistsException;
import com.example.retailstore.webapp.model.request.RegistrationRequest;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class KeycloakRegistrationService {

    private final Keycloak keycloak;
    private final String realm;

    /**
     * Builds an admin client authenticated against the master realm for user registration.
     *
     * @param props admin credentials, client configuration, and target registration realm
     * @param url Keycloak server URL
     */
    @Autowired
    public KeycloakRegistrationService(KeycloakProperties props, @Value("${OAUTH2_SERVER_URL}") String url) {
        this(
                props,
                KeycloakBuilder.builder()
                        .serverUrl(url)
                        .realm("master")
                        .clientId(props.getAdminClientId())
                        .clientSecret(props.getAdminClientSecret())
                        .username(props.getAdminUsername())
                        .password(props.getAdminPassword())
                        .grantType(OAuth2Constants.PASSWORD)
                        .build());
    }

    /**
     * Creates a registration service using an existing admin client.
     *
     * @param props configuration containing the target registration realm
     * @param keycloak client used to create users and assign realm roles
     */
    KeycloakRegistrationService(KeycloakProperties props, Keycloak keycloak) {
        this.realm = props.getRealm();
        this.keycloak = keycloak;
    }

    /**
     * Creates an enabled account and then assigns the realm's {@code user} role.
     *
     * <p>Role assignment occurs after account creation; a role assignment failure leaves the account
     * in Keycloak. The user creation response is closed on success or failure.
     *
     * @param request profile and password for the new account
     * @throws UserAlreadyExistsException if user creation returns HTTP 409
     * @throws KeyCloakException if user creation returns another status of 400 or higher
     */
    public void registerUser(RegistrationRequest request) {
        UserRepresentation user = getUserRepresentation(request);

        RealmResource realmResource = keycloak.realm(realm);
        try (Response response = realmResource.users().create(user)) {
            if (response.getStatus() == 409) {
                throw new UserAlreadyExistsException("Username or email is already taken.");
            } else if (response.getStatus() >= 400) {
                throw new KeyCloakException("Failed to register user. Status: " + response.getStatus());
            }
            String userId = CreatedResponseUtil.getCreatedId(response);
            realmResource
                    .users()
                    .get(userId)
                    .roles()
                    .realmLevel()
                    .add(List.of(realmResource.roles().get("user").toRepresentation()));
        }
    }

    /**
     * Maps registration details to an enabled Keycloak user with a non-temporary password.
     *
     * @param request profile and password for the new account
     * @return user representation ready for creation
     */
    private static @NonNull UserRepresentation getUserRepresentation(RegistrationRequest request) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEnabled(true);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(request.password());
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));
        return user;
    }
}
