package com.example.retailstore.webapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(SecurityConfigTest.PublicController.class)
@Import({SecurityConfig.class, SecurityConfigTest.PublicController.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void shouldDisallowInlineScriptsInPublicResponses() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                        "Content-Security-Policy",
                                        "default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://example.com; connect-src 'self'"));
    }

    @RestController
    static class PublicController {
        @GetMapping("/")
        String home() {
            return "home";
        }
    }

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
