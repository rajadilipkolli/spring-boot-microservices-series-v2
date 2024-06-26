package com.example.retailstore.webapp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class SecurityHelperTest {

    @Test
    void testGetLoggedInUserEmail_Success() {
        // Setup
        SecurityHelper securityHelper =
                new SecurityHelper(null); // OAuth2AuthorizedClientService is not used in this method
        OAuth2AuthenticationToken oauthToken = mock(OAuth2AuthenticationToken.class);
        DefaultOidcUser user = mock(DefaultOidcUser.class);
        given(user.getEmail()).willReturn("user@example.com");
        given(oauthToken.getPrincipal()).willReturn(user);
        SecurityContextHolder.getContext().setAuthentication(oauthToken);

        // Execute
        String email = securityHelper.getLoggedInUserEmail();

        // Verify
        assertThat(email).isEqualTo("user@example.com");
    }

    @Test
    void testGetLoggedInUserEmail_Failure() {
        // Setup
        SecurityHelper securityHelper =
                new SecurityHelper(null); // OAuth2AuthorizedClientService is not used in this method
        SecurityContextHolder.getContext().setAuthentication(null);

        // Execute
        String email = securityHelper.getLoggedInUserEmail();

        // Verify
        assertThat(email).isNull();
    }
}
