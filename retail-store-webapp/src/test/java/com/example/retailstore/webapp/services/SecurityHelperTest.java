package com.example.retailstore.webapp.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

class SecurityHelperTest {

    SecurityHelper securityHelper = new SecurityHelper();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testGetLoggedInUserEmail_Success() {
        // Setup
        OAuth2AuthenticationToken oauthToken = mock(OAuth2AuthenticationToken.class);
        OidcUser user = mock(OidcUser.class);
        given(user.getEmail()).willReturn("user@example.com");
        given(oauthToken.getPrincipal()).willReturn(user);
        SecurityContextHolder.getContext().setAuthentication(oauthToken);

        // Execute
        String email = securityHelper.getLoggedInUserEmail();

        // Verify
        assertThat(email).isEqualTo("user@example.com");
    }

    @Test
    void testGetLoggedInUserEmail_OAuth2User_Success() {
        // Setup
        OAuth2AuthenticationToken oauthToken = mock(OAuth2AuthenticationToken.class);
        OAuth2User user = mock(OAuth2User.class);
        given(user.getAttribute("email")).willReturn("oauth2@example.com");
        given(oauthToken.getPrincipal()).willReturn(user);
        SecurityContextHolder.getContext().setAuthentication(oauthToken);

        // Execute
        String email = securityHelper.getLoggedInUserEmail();

        // Verify
        assertThat(email).isEqualTo("oauth2@example.com");
    }

    @Test
    void testGetLoggedInUserEmail_Failure() {
        // Setup
        SecurityContextHolder.getContext().setAuthentication(null);

        // Execute
        String email = securityHelper.getLoggedInUserEmail();

        // Verify
        assertThat(email).isNull();
    }
}
