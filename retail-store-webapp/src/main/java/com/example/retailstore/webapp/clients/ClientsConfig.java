package com.example.retailstore.webapp.clients;

import com.example.retailstore.webapp.clients.catalog.CatalogServiceClient;
import com.example.retailstore.webapp.clients.customer.CustomerServiceClient;
import com.example.retailstore.webapp.clients.inventory.InventoryServiceClient;
import com.example.retailstore.webapp.clients.order.OrderServiceClient;
import com.example.retailstore.webapp.config.ApplicationProperties;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration(proxyBeanMethods = false)
@ImportHttpServices({
    CatalogServiceClient.class,
    OrderServiceClient.class,
    CustomerServiceClient.class,
    InventoryServiceClient.class
})
class ClientsConfig {

    /**
     * Configures service clients with the gateway URL, JSON headers, OAuth2, and observations.
     *
     * @param observationRegistry registry for HTTP client observations
     * @param applicationProperties configuration containing the API gateway URL
     * @param authorizedClientManager manager used to authorize outgoing OAuth2 requests
     * @return configurer applied to the imported HTTP service clients
     */
    @Bean
    RestClientHttpServiceGroupConfigurer groupConfigurer(
            ObservationRegistry observationRegistry,
            ApplicationProperties applicationProperties,
            OAuth2AuthorizedClientManager authorizedClientManager) {

        OAuth2ClientHttpRequestInterceptor interceptor =
                new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);

        return groups -> groups.forEachClient((group, builder) -> builder.baseUrl(applicationProperties.apiGatewayUrl())
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                    httpHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                })
                .requestInterceptor(interceptor)
                .observationRegistry(observationRegistry)
                .build());
    }
}
