/***
<p>
    Licensed under MIT License Copyright (c) 2022-2024 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import com.example.orderservice.services.CatalogServiceProxy;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration(proxyBeanMethods = false)
@ImportHttpServices(CatalogServiceProxy.class)
class HttpClientConfig {

    @Bean
    RestClientHttpServiceGroupConfigurer groupConfigurer(
            ObservationRegistry observationRegistry, ApplicationProperties applicationProperties) {
        return groups ->
                groups.forEachClient(
                        (_, builder) -> builder.observationRegistry(observationRegistry).build());
    }
}
