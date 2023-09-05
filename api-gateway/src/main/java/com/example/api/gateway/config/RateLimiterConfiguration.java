/* Licensed under Apache-2.0 2023 */
package com.example.api.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
public class RateLimiterConfiguration {

    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> Mono.just("1");
    }
}
