package com.example.retailstore.webapp.services;

import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service("securityHelper")
public class SecurityHelper {

    /** Creates a helper that reads the current thread's security context. */
    public SecurityHelper() {}

    public String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = null;
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof OAuth2User principal) {
            username = principal.getAttribute("preferred_username");
        }
        return username;
    }

    public String getLoggedInUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (authentication instanceof OAuth2AuthenticationToken
                && authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            if (oauth2User instanceof OidcUser oidcUser) {
                String email = oidcUser.getEmail();
                return email != null ? email.toLowerCase(Locale.ROOT) : null;
            }
            String email = oauth2User.getAttribute("email");
            return email != null ? email.toLowerCase(Locale.ROOT) : null;
        }
        return authentication.getName().toLowerCase(Locale.ROOT);
    }
}
