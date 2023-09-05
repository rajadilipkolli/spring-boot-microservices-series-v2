/* Licensed under Apache-2.0 2023 */
package com.example.api.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.server.SecurityWebFilterChain;

@EnableWebFluxSecurity
@Configuration(proxyBeanMethods = false)
@Slf4j
public class SecurityConfig {

    @Bean
    MapReactiveUserDetailsService users() {
        UserDetails user1 =
                User.builder().username("user1").password("{noop}1234").roles("USER").build();
        UserDetails user2 =
                User.builder().username("user2").password("{noop}1234").roles("USER").build();
        UserDetails user3 =
                User.builder().username("user3").password("{noop}1234").roles("USER").build();
        return new MapReactiveUserDetailsService(user1, user2, user3);
    }

    @Bean
    KeyResolver authUserKeyResolver() {
        return exchange ->
                ReactiveSecurityContextHolder.getContext()
                        .map(
                                ctx -> {
                                    String user = ctx.getAuthentication().getPrincipal().toString();
                                    log.debug("User from authUserKeyResolver :{}", user);
                                    return user;
                                });
    }

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .httpBasic(Customizer.withDefaults())
                .csrf(ServerHttpSecurity.CsrfSpec::disable);
        return http.build();
    }
}
