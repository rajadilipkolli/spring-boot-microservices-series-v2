/* Licensed under Apache-2.0 2023 */
package com.example.catalogservice.config;

import com.example.catalogservice.services.InventoryServiceProxy;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class HttpClientConfig {

    private final ApplicationProperties applicationProperties;

    @Bean
    public HttpServiceProxyFactory httpServiceProxyFactory(WebClient.Builder builder) {
        WebClient webClient =
                builder.baseUrl(applicationProperties.getInventoryServiceUrl())
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build();
        return HttpServiceProxyFactory.builder(WebClientAdapter.forClient(webClient)).build();
    }

    @Bean
    public InventoryServiceProxy catalogServiceProxy(
            HttpServiceProxyFactory httpServiceProxyFactory) {
        return httpServiceProxyFactory.createClient(InventoryServiceProxy.class);
    }
}
