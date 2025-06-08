package com.example.retailstore.webapp.services;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;

@Service
public class SecurityHelper {
    private final OAuth2AuthorizedClientService authorizedClientService;

    public SecurityHelper(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
    }

    public String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = null;
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof DefaultOidcUser principal) {
            username = principal.getAttribute("preferred_username");
        }
        return username;
    }

    public String getAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            return null;
        }
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());

        if (client == null) {
            // Log or handle the case where the client is not found
            // For now, returning null or throwing a specific exception might be appropriate
            return null;
        }

        return client.getAccessToken().getTokenValue();
    }

    public String getLoggedInUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OAuth2AuthenticationToken)) {
            return null;
        }
        DefaultOidcUser principal = (DefaultOidcUser) authentication.getPrincipal();
        return principal.getEmail();
    }
}
