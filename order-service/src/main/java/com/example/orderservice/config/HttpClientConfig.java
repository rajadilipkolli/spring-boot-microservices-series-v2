/* Licensed under Apache-2.0 2022 */
package com.example.orderservice.config;

import com.example.orderservice.services.CatalogServiceProxy;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class HttpClientConfig {

    private final ApplicationProperties applicationProperties;

    @Bean
    public HttpServiceProxyFactory httpServiceProxyFactory(WebClient.Builder builder) {
        WebClient webClient = builder.baseUrl(applicationProperties.catalogServiceUrl()).build();
        return HttpServiceProxyFactory.builder(WebClientAdapter.forClient(webClient)).build();
    }

    @Bean
    public CatalogServiceProxy catalogServiceProxy(
            HttpServiceProxyFactory httpServiceProxyFactory) {
        return httpServiceProxyFactory.createClient(CatalogServiceProxy.class);
    }
}
