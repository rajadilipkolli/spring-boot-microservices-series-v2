package com.example.retailstore.webapp.config;

import org.jspecify.annotations.NonNull;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.JacksonProvider;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.spi.ResteasyClientClassicProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebConfig {

    @Bean
    WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:8080")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600); // Cache preflight response for 1 hour
            }
        };
    }

    @Bean
    Keycloak keycloakClient(KeycloakProperties props, @Value("${OAUTH2_SERVER_URL}") String url) {
        return KeycloakBuilder.builder()
                .serverUrl(url)
                .realm("master")
                .clientId(props.getAdminClientId())
                .clientSecret(props.getAdminClientSecret())
                .username(props.getAdminUsername())
                .password(props.getAdminPassword())
                .grantType(OAuth2Constants.PASSWORD)
                .resteasyClient(ResteasyClientClassicProvider.createClientBuilder()
                        .connectionPoolSize(20)
                        .build()
                        .register(JacksonProvider.class, 100))
                .build();
    }
}
