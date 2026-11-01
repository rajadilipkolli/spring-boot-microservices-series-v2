package com.example.retailstore.webapp.services;

import com.example.retailstore.webapp.model.request.RegistrationRequest;

/**
 * Port interface for identity provider operations such as user registration and deletion.
 */
public interface IdentityProviderPort {

    /**
     * Registers a user with the identity provider.
     *
     * @param request profile and credentials for the new user account
     */
    void registerUser(RegistrationRequest request);

    /**
     * Deletes a user account matching the username.
     *
     * @param username the username to remove from the identity provider
     */
    void deleteUserByUsername(String username);
}
