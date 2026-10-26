package com.example.retailstore.webapp.web.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.retailstore.webapp.config.SecurityConfig;
import com.example.retailstore.webapp.services.SecurityHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LoginController.class)
@Import({SecurityConfig.class, SecurityHelper.class})
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    @WithAnonymousUser
    void shouldAllowAnonymousAccessToLoginPage() throws Exception {
        mockMvc.perform(get("/login").with(csrf())).andExpect(status().isOk()).andExpect(view().name("login"));
    }

    @Test
    void shouldRedirectAuthenticatedUserFromLoginPage() throws Exception {
        mockMvc.perform(get("/login")
                        .with(oauth2Login()
                                .attributes(attrs -> {
                                    attrs.put("sub", "user-id");
                                    attrs.put("email", "user@example.com");
                                    attrs.put("preferred_username", "user@example.com");
                                })
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
