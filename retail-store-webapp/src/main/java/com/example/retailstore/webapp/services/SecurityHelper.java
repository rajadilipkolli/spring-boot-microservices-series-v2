package com.example.retailstore.webapp.services;

import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Service;

@Service
public class SecurityHelper {
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public SecurityHelper(OAuth2AuthorizedClientManager authorizedClientManager) {
        this.authorizedClientManager = authorizedClientManager;
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
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(
                        oauthToken.getAuthorizedClientRegistrationId())
                .principal(oauthToken)
                .build();

        OAuth2AuthorizedClient client = this.authorizedClientManager.authorize(authorizeRequest);

        if (client == null || client.getAccessToken() == null) {
            return null;
        }

        return client.getAccessToken().getTokenValue();
    }

    public String getLoggedInUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (authentication instanceof OAuth2AuthenticationToken) {
            DefaultOidcUser principal = (DefaultOidcUser) authentication.getPrincipal();
            String email = principal.getEmail();
            return email != null ? email.toLowerCase(Locale.ROOT) : null;
        }
        return authentication.getName().toLowerCase(Locale.ROOT);
    }
}
