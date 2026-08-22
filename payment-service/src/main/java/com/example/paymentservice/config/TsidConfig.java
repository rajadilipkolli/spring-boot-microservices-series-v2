/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.config;

import io.hypersistence.tsid.TSID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TsidConfig {

    @Bean
    public TSID.Factory tsidFactory() {
        String nodeStr = System.getProperty("tsid.node", System.getenv("TSID_NODE"));
        int node = (nodeStr != null && !nodeStr.trim().isEmpty()) ? Integer.parseInt(nodeStr) : 0;
        return TSID.Factory.builder().withNode(node).build();
    }
}
