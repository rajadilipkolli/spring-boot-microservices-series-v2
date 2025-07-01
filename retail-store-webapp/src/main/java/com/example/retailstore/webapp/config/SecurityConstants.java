package com.example.retailstore.webapp.config;

public final class SecurityConstants {
    private SecurityConstants() {} // Prevent instantiation

    public static final String KEYCLOAK_REALM = "retailstore";
    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "admin1234";
    public static final String[] PUBLIC_URLS = {
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
        "/login"
    };
}
