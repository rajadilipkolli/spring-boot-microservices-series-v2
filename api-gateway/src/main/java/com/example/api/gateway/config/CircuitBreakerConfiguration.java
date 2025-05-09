/***
<p>
    Licensed under MIT License Copyright (c) 2021-2022 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.time.Duration;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
class CircuitBreakerConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory ->
                factory.configureDefault(
                        id ->
                                new Resilience4JConfigBuilder(id)
                                        .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
                                        .timeLimiterConfig(
                                                TimeLimiterConfig.custom()
                                                        .timeoutDuration(Duration.ofSeconds(5))
                                                        .build())
                                        .build());
    }
}
