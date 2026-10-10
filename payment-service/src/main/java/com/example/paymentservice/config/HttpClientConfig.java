/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.config;

import com.example.paymentservice.services.OrderServiceProxy;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = "order", types = OrderServiceProxy.class)
class HttpClientConfig {

    /**
     * Returns a configurer that applies the configured base URL and observation registry to
     * clients in the {@code order} HTTP service group.
     */
    @Bean
    RestClientHttpServiceGroupConfigurer groupConfigurer(
            ObservationRegistry observationRegistry, ApplicationProperties applicationProperties) {
        return groups ->
                groups.filterByName("order")
                        .forEachClient(
                                (group, builder) ->
                                        builder.baseUrl(applicationProperties.getOrderServiceUrl())
                                                .observationRegistry(observationRegistry));
    }
}
