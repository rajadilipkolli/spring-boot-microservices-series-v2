/***
<p>
    Licensed under MIT License Copyright (c) 2022-2024 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import com.example.orderservice.services.CatalogServiceProxy;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
class HttpClientConfig {

    private final ApplicationProperties applicationProperties;

    HttpClientConfig(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Bean
    RestClientCustomizer restClientCustomizer(ObservationRegistry observationRegistry) {
        return restClientBuilder ->
                restClientBuilder
                        .defaultHeaders(
                                httpHeaders -> {
                                    httpHeaders.add(
                                            HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                                    httpHeaders.add(
                                            HttpHeaders.CONTENT_TYPE,
                                            MediaType.APPLICATION_JSON_VALUE);
                                })
                        .baseUrl(applicationProperties.catalogServiceUrl())
                        .observationRegistry(observationRegistry);
    }

    @Bean
    HttpServiceProxyFactory httpServiceProxyFactory(RestClient.Builder builder) {
        RestClient restClient = builder.build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient)).build();
    }

    @Bean
    CatalogServiceProxy catalogServiceProxy(HttpServiceProxyFactory httpServiceProxyFactory) {
        return httpServiceProxyFactory.createClient(CatalogServiceProxy.class);
    }
}
