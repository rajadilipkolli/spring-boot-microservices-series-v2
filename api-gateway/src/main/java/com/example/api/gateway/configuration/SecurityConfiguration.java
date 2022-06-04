/* Licensed under Apache-2.0 2021 */
package com.example.api.gateway.configuration;

import com.example.api.gateway.repository.UserRepository;
import com.example.api.gateway.security.jwt.JwtTokenAuthenticationFilter;
import com.example.api.gateway.security.jwt.JwtTokenProvider;
import org.springframework.boot.autoconfigure.security.reactive.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UserDetailsRepositoryReactiveAuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    private static final String[] AUTH_WHITELIST = {
        // -- Swagger UI v3 (OpenAPI)
        "/swagger-ui.html", "/v3/api-docs/**", "/swagger-ui/**", "/auth/**"
        // other public endpoints of your API may be appended to this array
    };

    @Bean
    SecurityWebFilterChain springWebFilterChain(
            ServerHttpSecurity http,
            JwtTokenProvider tokenProvider,
            ReactiveAuthenticationManager reactiveAuthenticationManager) {
        final String authenticationPaths = "/auth/**";

        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .authenticationManager(reactiveAuthenticationManager)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(
                        it ->
                                it.pathMatchers(HttpMethod.GET, AUTH_WHITELIST)
                                        .permitAll()
                                        .pathMatchers(HttpMethod.POST, authenticationPaths)
                                        .permitAll()
                                        .pathMatchers(HttpMethod.DELETE, authenticationPaths)
                                        .hasRole("ADMIN")
                                        .pathMatchers(authenticationPaths)
                                        .access(this::currentUserMatchesPath)
                                        .matchers(
                                                PathRequest.toStaticResources().atCommonLocations())
                                        .permitAll()
                                        .pathMatchers(HttpMethod.OPTIONS)
                                        .permitAll()
                                        .anyExchange()
                                        .authenticated())
                .addFilterAt(
                        new JwtTokenAuthenticationFilter(tokenProvider),
                        SecurityWebFiltersOrder.HTTP_BASIC)
                .build();
    }

    private Mono<AuthorizationDecision> currentUserMatchesPath(
            Mono<Authentication> authentication, AuthorizationContext context) {

        return authentication
                .map(a -> context.getVariables().get("user").equals(a.getName()))
                .map(AuthorizationDecision::new);
    }

    @Bean
    public ReactiveUserDetailsService userDetailsService(UserRepository userRepository) {

        return username ->
                userRepository
                        .findByUsername(username)
                        .map(
                                u ->
                                        User.withUsername(u.getUsername())
                                                .password(u.getPassword())
                                                .authorities(u.getRoles().toArray(new String[0]))
                                                .accountExpired(!u.isActive())
                                                .credentialsExpired(!u.isActive())
                                                .disabled(!u.isActive())
                                                .accountLocked(!u.isActive())
                                                .build());
    }

    @Bean
    public ReactiveAuthenticationManager reactiveAuthenticationManager(
            ReactiveUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        var authenticationManager =
                new UserDetailsRepositoryReactiveAuthenticationManager(userDetailsService);
        authenticationManager.setPasswordEncoder(passwordEncoder);
        return authenticationManager;
    }
}
