/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/
package com.example.api.gateway.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public Duration delayDuration() {
        return Duration.ofSeconds(10);
    }
}