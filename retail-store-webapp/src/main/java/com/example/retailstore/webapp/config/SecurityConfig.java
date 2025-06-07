package com.example.retailstore.webapp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
class SecurityConfig {
    private final ClientRegistrationRepository clientRegistrationRepository;

    SecurityConfig(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(c -> c.requestMatchers(
                                "/js/**",
                                "/css/**",
                                "/images/**",
                                "/error",
                                "/webjars/**",
                                "/",
                                "/actuator/**",
                                "/products/**",
                                "/api/products/**",
                                "/api/register",
                                "/registration",
                                "/login")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .cors(Customizer.withDefaults())
                .csrf(
                        csrf -> csrf.ignoringRequestMatchers("/api/register") // Allow registration without CSRF
                                .csrfTokenRepository(
                                        CookieCsrfTokenRepository.withHttpOnlyFalse()) // Store token in a cookie
                        )
                .oauth2Login(oauth2 -> oauth2.loginPage("/login").defaultSuccessUrl("/", true))
                .logout(logout -> logout.clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .logoutSuccessHandler(oidcLogoutSuccessHandler()));
        return http.build();
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler =
                new OidcClientInitiatedLogoutSuccessHandler(this.clientRegistrationRepository);
        oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");
        return oidcLogoutSuccessHandler;
    }
}
