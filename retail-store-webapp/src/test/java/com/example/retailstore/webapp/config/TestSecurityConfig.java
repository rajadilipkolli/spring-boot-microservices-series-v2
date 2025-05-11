package com.example.retailstore.webapp.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
@EnableWebSecurity
public class TestSecurityConfig {

    @Bean
    @Primary
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails testUser = User.builder()
                .username("user")
                .password(passwordEncoder.encode("password"))
                .roles("USER")
                .build();
        UserDetails testAdmin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("password"))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(testUser, testAdmin);
    }

    @Bean
    @Primary
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Primary
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(c -> c.requestMatchers(
                                "/login", "/", "/api/register", "/js/**", "/css/**", "/images/**", "/webjars/**")
                        .permitAll()
                        .requestMatchers("/inventory/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .csrf(csrf -> csrf.disable())
                .formLogin(
                        form -> form.loginPage("/login").defaultSuccessUrl("/").permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/").permitAll());
        return http.build();
    }
}
