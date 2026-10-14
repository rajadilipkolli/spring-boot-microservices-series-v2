/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.config;

import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JooqConfig {

    /**
     * Enables jOOQ optimistic locking and automatic record-version updates.
     *
     * @return the customizer applied to the jOOQ configuration
     */
    @Bean
    DefaultConfigurationCustomizer jooqCustomizer() {
        return configuration ->
                configuration.set(
                        configuration
                                .settings()
                                .withExecuteWithOptimisticLocking(true)
                                .withUpdateRecordVersion(true));
    }
}
