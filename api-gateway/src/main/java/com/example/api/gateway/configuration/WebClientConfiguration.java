/* Licensed under Apache-2.0 2023 */
package com.example.api.gateway.configuration;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
public class WebClientConfiguration {

    @Bean
    @LoadBalanced
    WebClient loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
