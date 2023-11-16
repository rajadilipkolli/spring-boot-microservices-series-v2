/***
<p>
    Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import com.example.orderservice.services.CatalogServiceProxy;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class HttpClientConfig {

    private final ApplicationProperties applicationProperties;

    @Bean
    HttpServiceProxyFactory httpServiceProxyFactory(
            RestClient.Builder builder, ObservationRegistry observationRegistry) {
        RestClient restClient =
                builder.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .baseUrl(applicationProperties.catalogServiceUrl())
                        .observationRegistry(observationRegistry)
                        .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
    }

    @Bean
    CatalogServiceProxy catalogServiceProxy(HttpServiceProxyFactory httpServiceProxyFactory) {
        return httpServiceProxyFactory.createClient(CatalogServiceProxy.class);
    }
}
