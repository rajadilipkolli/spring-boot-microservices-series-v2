/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

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
                    client.post().uri("/private").exchange().expectStatus().isUnauthorized();
                });
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
                    .GET("/", request -> ServerResponse.ok().build())
                    .POST("/private", request -> ServerResponse.ok().build())
                    .build();
        }
    }
}
