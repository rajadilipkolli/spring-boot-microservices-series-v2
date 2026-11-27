/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.config;

import com.example.common.tsid.TsidFactories;
import io.hypersistence.tsid.TSID;
import org.jooq.impl.DefaultRecordListenerProvider;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JooqConfig {

    /**
     * Enables jOOQ optimistic locking, automatic record-version updates, and TSID assignment for
     * customer, payment operation, ledger, and outbox records.
     *
     * @param tsidFactory the factory used to generate missing record IDs
     * @return the customizer applied to the jOOQ configuration
     */
    @Bean
    DefaultConfigurationCustomizer jooqCustomizer(TSID.Factory tsidFactory) {
        TsidRecordListener tsidRecordListener = new TsidRecordListener(tsidFactory);
        return configuration -> {
            configuration.set(
                    configuration
                            .settings()
                            .withExecuteWithOptimisticLocking(true)
                            .withUpdateRecordVersion(true));
            configuration.set(new DefaultRecordListenerProvider(tsidRecordListener));
        };
    }

    /**
     * Builds the record ID factory, parsing a nonblank {@code tsid.node} system property or, when
     * the property is absent, {@code TSID_NODE} environment variable as a decimal integer. The
     * value is parsed without trimming; absent or blank values select a random node within the
     * configured node-bit range.
     *
     * @return the configured TSID factory
     * @throws NumberFormatException if the nonblank node value is not a decimal integer in the
     *     {@code int} range
     * @throws IllegalArgumentException if the node is outside the configured node-bit range or the
     *     builder's node count requires an unsupported bit length
     */
    @Bean
    TSID.Factory tsidFactory() {
        return TsidFactories.fromEnvironment();
    }
}
