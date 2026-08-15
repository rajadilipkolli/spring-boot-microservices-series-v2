package com.example.retailstore.webapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

class SecurityConfigTest {

    @Test
    void shouldMapKeycloakRolesToGrantedAuthorities() {
        // Arrange
        SecurityConfig config = new SecurityConfig(mock(ClientRegistrationRepository.class));
        GrantedAuthoritiesMapper mapper = config.userAuthoritiesMapper();

        Map<String, Object> claims = Map.of(
                "azp",
                "retailstore-webapp",
                "resource_access",
                Map.of("retailstore-webapp", Map.of("roles", List.of("ADMIN", "USER"))));

        OidcIdToken idToken =
                new OidcIdToken("token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
        OidcUserAuthority oidcUserAuthority = new OidcUserAuthority(idToken, null);

        // Act
        Collection<? extends GrantedAuthority> mappedAuthorities = mapper.mapAuthorities(Set.of(oidcUserAuthority));

        // Assert
        assertThat(mappedAuthorities).extracting(GrantedAuthority::getAuthority).contains("ROLE_ADMIN", "ROLE_USER");
    }
}
