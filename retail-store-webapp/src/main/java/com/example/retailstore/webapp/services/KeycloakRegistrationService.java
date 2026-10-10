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

    KeycloakRegistrationService(KeycloakProperties props, Keycloak keycloak) {
        this.realm = props.getRealm();
        this.keycloak = keycloak;
    }

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
