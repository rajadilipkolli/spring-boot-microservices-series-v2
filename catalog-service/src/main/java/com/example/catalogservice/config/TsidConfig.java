/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.config;

import io.hypersistence.tsid.TSID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TsidConfig {

    /**
     * Creates a TSID factory using {@code tsid.node}, falling back to {@code TSID_NODE} only when
     * the system property is absent. A blank value leaves the factory's default node selection.
     *
     * @return the configured identifier factory
     * @throws NumberFormatException if a nonblank node value cannot be parsed as an integer
     */
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
