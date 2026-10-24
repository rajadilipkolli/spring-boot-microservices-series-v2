/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.config;

import com.example.common.tsid.TsidFactories;
import io.hypersistence.tsid.TSID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TsidConfig {

    /**
     * Creates a TSID factory using {@code tsid.node}, falling back to {@code TSID_NODE} only when
     * the system property is absent. An absent or blank value selects a random node within the
     * configured node-bit range.
     *
     * @return the configured identifier factory
     * @throws NumberFormatException if a nonblank node value cannot be parsed as an integer
     * @throws IllegalArgumentException if the node is outside the configured node-bit range or the
     *     builder's node count requires an unsupported bit length
     */
    @Bean
    TSID.Factory tsidFactory() {
        return TsidFactories.fromEnvironment();
    }
}
