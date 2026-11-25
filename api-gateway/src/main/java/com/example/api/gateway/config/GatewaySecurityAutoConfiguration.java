/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.reactive.ReactiveOAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.util.StringUtils;

@AutoConfiguration(
        after = ReactiveOAuth2ResourceServerAutoConfiguration.class,
        before = ReactiveOAuth2ResourceServerWebSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = Type.REACTIVE)
@EnableWebFluxSecurity
class GatewaySecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReactiveJwtDecoder.class)
    @ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.issuer-uri")
    ReactiveJwtDecoder reactiveJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri) {
        String effectiveJwkSetUri =
                StringUtils.hasText(jwkSetUri)
                        ? jwkSetUri
                        : (issuerUri.replaceAll("/+$", "") + "/protocol/openid-connect/certs");
        NimbusReactiveJwtDecoder decoder =
                NimbusReactiveJwtDecoder.withJwkSetUri(effectiveJwkSetUri).build();

        OAuth2TokenValidator<Jwt> issuerValidator =
                token -> {
                    String iss = token.getClaimAsString(JwtClaimNames.ISS);
                    if (iss == null) {
                        return OAuth2TokenValidatorResult.failure(
                                new OAuth2Error(
                                        "invalid_token",
                                        "Missing iss claim in JWT token",
                                        "https://tools.ietf.org/html/rfc6750#section-3.1"));
                    }
                    if (iss.equals(issuerUri)
                            || iss.endsWith("/realms/retailstore")
                            || (issuerUri.endsWith("/realms/retailstore")
                                    && iss.contains("/realms/retailstore"))) {
                        return OAuth2TokenValidatorResult.success();
                    }
                    return OAuth2TokenValidatorResult.failure(
                            new OAuth2Error(
                                    "invalid_token",
                                    "The Issuer \""
                                            + iss
                                            + "\" is not acceptable for configured issuer \""
                                            + issuerUri
                                            + "\"",
                                    "https://tools.ietf.org/html/rfc6750#section-3.1"));
                };

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtTypeValidator.jwt());
        validators.add(new JwtTimestampValidator(Duration.ofSeconds(60)));
        validators.add(issuerValidator);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    @Bean
    @ConditionalOnBean(ReactiveJwtDecoder.class)
    SecurityWebFilterChain springSecurityFilterChainWithJwt(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(authorize ->
                        authorize
                                .pathMatchers(
                                        "/actuator/**",
                                        "/*-service/actuator/**",
                                        "/v3/api-docs/**",
                                        "/*-service/v3/api-docs/**",
                                        "/*-service/v3/api-docs",
                                        "/swagger-ui/**",
                                        "/swagger-ui.html",
                                        "/webjars/**",
                                        "/fallback/**",
                                        "/api/v1/generate",
                                        "/")
                                .permitAll()
                                .anyExchange()
                                .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(ReactiveJwtDecoder.class)
    SecurityWebFilterChain springSecurityFilterChainPermitAll(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(authorize -> authorize.anyExchange().permitAll())
                .build();
    }
}
