/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.reactive.ReactiveOAuth2ResourceServerWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

class GatewaySecurityAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner contextRunner =
            new ReactiveWebApplicationContextRunner()
                    .withUserConfiguration(TestRoutes.class)
                    .withConfiguration(
                            AutoConfigurations.of(
                                    GatewaySecurityAutoConfiguration.class,
                                    ReactiveOAuth2ResourceServerAutoConfiguration.class,
                                    ReactiveOAuth2ResourceServerWebSecurityAutoConfiguration
                                            .class));

    @Test
    void registersGatewaySecurityAutoConfiguration() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(GatewaySecurityAutoConfiguration.class.getName());
    }

    @Test
    void requiresAuthenticationForNonPublicExchangesWhenJwtDecodingIsConfigured() {
        contextRunner
                .withPropertyValues(
                        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/issuer")
                .run(context -> {
                    assertThat(context).hasSingleBean(ReactiveJwtDecoder.class);
                    assertThat(context).hasSingleBean(SecurityWebFilterChain.class);
                    WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
                    client.get().uri("/").exchange().expectStatus().isOk();
                    client.get().uri("/actuator/health").exchange().expectStatus().isOk();
                    client.get()
                            .uri("/catalog-service/actuator/health")
                            .exchange()
                            .expectStatus()
                            .isOk();
                    client.get().uri("/order-service/v3/api-docs").exchange().expectStatus().isOk();
                    client.post().uri("/private").exchange().expectStatus().isUnauthorized();
                });
    }

    @Test
    void validatesSignedTokensUsingTheConfiguredIssuerAndSeparateKeyEndpoint() throws Exception {
        String issuer = "http://keycloak.local/realms/retailstore";
        var signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        byte[] jwks =
                new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/keys",
                exchange -> {
                    try (exchange) {
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, jwks.length);
                        exchange.getResponseBody().write(jwks);
                    }
                });
        server.start();
        try {
            var signer = new RSASSASigner(signingKey);
            contextRunner
                    .withPropertyValues(
                            "spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer,
                            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:"
                                    + server.getAddress().getPort()
                                    + "/keys")
                    .run(context -> {
                        assertThat(context).hasSingleBean(ReactiveJwtDecoder.class);
                        WebTestClient client =
                                WebTestClient.bindToApplicationContext(context).build();
                        authenticate(client, signer, issuer, "JWT", 120).expectStatus().isOk();
                        authenticate(client, signer, issuer, "JWT", -30).expectStatus().isOk();
                        authenticate(client, signer, issuer, "JWT", -90)
                                .expectStatus()
                                .isUnauthorized();
                        authenticate(client, signer, issuer, "at+jwt", 120)
                                .expectStatus()
                                .isUnauthorized();
                        authenticate(
                                        client,
                                        signer,
                                        "http://other.example/realms/retailstore",
                                        "JWT",
                                        120)
                                .expectStatus()
                                .isUnauthorized();
                        authenticate(client, signer, issuer + "/other", "JWT", 120)
                                .expectStatus()
                                .isUnauthorized();
                    });
        } finally {
            server.stop(0);
        }
    }

    private WebTestClient.ResponseSpec authenticate(
            WebTestClient client, RSASSASigner signer, String issuer, String type, int expiresIn)
            throws Exception {
        var token =
                new SignedJWT(
                        new JWSHeader.Builder(JWSAlgorithm.RS256)
                                .type(new JOSEObjectType(type))
                                .keyID("test-key")
                                .build(),
                        new JWTClaimsSet.Builder()
                                .subject("retail")
                                .issuer(issuer)
                                .expirationTime(Date.from(Instant.now().plusSeconds(expiresIn)))
                                .build());
        token.sign(signer);
        return client.post()
                .uri("/private")
                .headers(headers -> headers.setBearerAuth(token.serialize()))
                .exchange();
    }

    @Test
    void permitsNonPublicExchangesWhenNoDecoderIsConfigured() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ReactiveJwtDecoder.class);
            assertThat(context).hasSingleBean(SecurityWebFilterChain.class);
            WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
            client.post().uri("/private").exchange().expectStatus().isOk();
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebFlux
    static class TestRoutes {

        @Bean
        RouterFunction<ServerResponse> routes() {
            return RouterFunctions.route()
                    .GET("/", _ -> ServerResponse.ok().build())
                    .GET("/actuator/health", _ -> ServerResponse.ok().build())
                    .GET("/catalog-service/actuator/health", _ -> ServerResponse.ok().build())
                    .GET("/order-service/v3/api-docs", _ -> ServerResponse.ok().build())
                    .POST("/private", _ -> ServerResponse.ok().build())
                    .build();
        }
    }
}
