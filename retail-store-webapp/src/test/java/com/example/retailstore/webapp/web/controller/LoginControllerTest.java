package com.example.retailstore.webapp.web.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.retailstore.webapp.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LoginController.class)
@Import(TestSecurityConfig.class)
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
