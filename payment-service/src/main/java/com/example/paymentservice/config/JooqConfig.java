/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.config;

import io.hypersistence.tsid.TSID;
import org.jooq.impl.DefaultRecordListenerProvider;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JooqConfig {

    /**
     * Enables jOOQ optimistic locking, automatic record-version updates, and customer record
     * listener.
     *
     * @param tsidFactory the factory used to generate TSIDs for new customer records
     * @return the customizer applied to the jOOQ configuration
     */
    @Bean
    DefaultConfigurationCustomizer jooqCustomizer(TSID.Factory tsidFactory) {
        CustomerRecordListener customerRecordListener = new CustomerRecordListener(tsidFactory);
        return configuration -> {
            configuration.set(
                    configuration
                            .settings()
                            .withExecuteWithOptimisticLocking(true)
                            .withUpdateRecordVersion(true));
            configuration.set(new DefaultRecordListenerProvider(customerRecordListener));
        };
    }

    @Bean
    TSID.Factory tsidFactory() {
        String nodeStr = System.getProperty("tsid.node", System.getenv("TSID_NODE"));
        TSID.Factory.Builder builder = TSID.Factory.builder();
        if (nodeStr != null && !nodeStr.trim().isEmpty()) {
            builder.withNode(Integer.parseInt(nodeStr));
        }
        return builder.build();
    }
}
