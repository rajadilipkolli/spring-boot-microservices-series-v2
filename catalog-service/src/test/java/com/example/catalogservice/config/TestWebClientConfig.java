/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

@TestConfiguration
public class TestWebClientConfig {

    /**
     * Overrides the @LoadBalanced WebClient.Builder from WebClientConfiguration. This is necessary
     * for integration tests because mockWebServer returns standard http://localhost:<port> URLs. If
     * a @LoadBalanced WebClient is used, it attempts to resolve 'localhost' as a service ID via
     * Eureka, which results in an UnknownHostException.
     */
    @Primary
    @Bean(name = "testLoadBalancedWebClientBuilder")
    @Qualifier("loadBalanced") WebClient.Builder testWebClientBuilder() {
        return WebClient.builder();
    }
}
