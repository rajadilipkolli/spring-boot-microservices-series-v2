package com.example.retailstore.webapp.clients;

import com.example.retailstore.webapp.clients.catalog.CatalogServiceClient;
import com.example.retailstore.webapp.clients.customer.CustomerServiceClient;
import com.example.retailstore.webapp.clients.inventory.InventoryServiceClient;
import com.example.retailstore.webapp.clients.order.OrderServiceClient;
import com.example.retailstore.webapp.config.ApplicationProperties;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration(proxyBeanMethods = false)
class ClientsConfig {
    private final ApplicationProperties properties;

    ClientsConfig(ApplicationProperties properties) {
        this.properties = properties;
    }

    @Bean
    RestClientCustomizer restClientCustomizer(ObservationRegistry observationRegistry) {
        return restClientBuilder -> restClientBuilder
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                    httpHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                })
                .observationRegistry(observationRegistry);
    }

    @Bean
    CatalogServiceClient catalogServiceClient(RestClient.Builder builder) {
        RestClient restClient =
                builder.baseUrl(properties.apiGatewayUrl() + "/catalog-service").build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(CatalogServiceClient.class);
    }

    @Bean
    OrderServiceClient orderServiceClient(RestClient.Builder builder) {
        RestClient restClient =
                builder.baseUrl(properties.apiGatewayUrl() + "/order-service").build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(OrderServiceClient.class);
    }

    @Bean
    CustomerServiceClient customerServiceClient(RestClient.Builder builder) {
        RestClient restClient =
                builder.baseUrl(properties.apiGatewayUrl() + "/payment-service").build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(CustomerServiceClient.class);
    }

    @Bean
    InventoryServiceClient inventoryServiceClient(RestClient.Builder builder) {
        RestClient restClient = builder.baseUrl(properties.apiGatewayUrl() + "/inventory-service")
                .build();
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build();
        return factory.createClient(InventoryServiceClient.class);
    }
}
