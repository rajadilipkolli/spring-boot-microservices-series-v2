/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/
package com.example.api.gateway.config;

import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@TestConfiguration
@Profile("test")
public class TestDelayConfiguration {

    @Bean
    @Primary
    public Duration testDelay() {
        String delaySeconds = System.getProperty("test.wiremock.delay-seconds", "1");
        return Duration.ofSeconds(Integer.parseInt(delaySeconds));
    }
}