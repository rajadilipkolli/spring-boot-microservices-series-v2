package com.example.retailstore.webapp.services;

import com.example.retailstore.webapp.config.KeycloakProperties;
import com.example.retailstore.webapp.exception.KeyCloakException;
import com.example.retailstore.webapp.exception.UserAlreadyExistsException;
import com.example.retailstore.webapp.model.request.RegistrationRequest;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

@Service
public class KeycloakRegistrationService {

    private final Keycloak keycloakClient;
    private final String realm;

    /**
     * Creates a registration service using an existing admin client.
     *
     * @param props configuration containing the target registration realm
     * @param keycloakClient client used to create users and assign realm roles
     */
    KeycloakRegistrationService(KeycloakProperties props, Keycloak keycloakClient) {
        this.realm = props.getRealm();
        this.keycloakClient = keycloakClient;
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

        RealmResource realmResource = keycloakClient.realm(realm);
        try (Response response = realmResource.users().create(user)) {
            if (response.getStatus() == 409) {
                throw new UserAlreadyExistsException("Username or email is already taken.");
            } else if (response.getStatus() >= 400) {
                throw new KeyCloakException("Failed to register user. Status: " + response.getStatus());
            }
            String userId = CreatedResponseUtil.getCreatedId(response);
            try {
                realmResource
                        .users()
                        .get(userId)
                        .roles()
                        .realmLevel()
                        .add(List.of(realmResource.roles().get("user").toRepresentation()));
            } catch (Exception e) {
                try {
                    realmResource.users().get(userId).remove();
                } catch (Exception cleanupException) {
                    e.addSuppressed(cleanupException);
                }
                throw e;
            }
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
