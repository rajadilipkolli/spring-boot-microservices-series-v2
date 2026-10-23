/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.orderservice.config.ApplicationProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogServiceTest {

    @Test
    void productsExistsByCodes_WhenProxySucceeds_ShouldReturnProxyResponse() {
        CatalogServiceProxy proxy = codes -> new CatalogServiceProxy.ProductExistsResponse(true);
        ApplicationProperties properties =
                new ApplicationProperties("http://localhost:8080", false, null);
        CatalogService catalogService = new CatalogService(proxy, properties);
        List<String> codes = List.of("P001", "P002");

        CatalogServiceProxy.ProductExistsResponse response =
                catalogService.productsExistsByCodes(codes);

        assertThat(response.exists()).isTrue();
    }

    @Test
    void productsExistsDefaultValue_WhenFailClosed_ShouldReturnFalse() {
        // byPassCircuitBreaker = false means fail-closed (reject orders when catalog is down)
        CatalogServiceProxy proxy =
                codes -> {
                    throw new RuntimeException("Catalog service down");
                };
        ApplicationProperties properties =
                new ApplicationProperties("http://localhost:8080", false, null);
        CatalogService catalogService = new CatalogService(proxy, properties);
        List<String> codes = List.of("P001");

        CatalogServiceProxy.ProductExistsResponse fallbackResponse =
                catalogService.productsExistsDefaultValue(
                        codes, new RuntimeException("Catalog service down"));

        assertThat(fallbackResponse.exists()).isFalse();
    }

    @Test
    void productsExistsDefaultValue_WhenFailOpenConfigured_ShouldReturnTrue() {
        // byPassCircuitBreaker = true means fail-open (only when explicitly configured)
        CatalogServiceProxy proxy =
                codes -> {
                    throw new RuntimeException("Catalog service down");
                };
        ApplicationProperties properties =
                new ApplicationProperties("http://localhost:8080", true, null);
        CatalogService catalogService = new CatalogService(proxy, properties);
        List<String> codes = List.of("P001");

        CatalogServiceProxy.ProductExistsResponse fallbackResponse =
                catalogService.productsExistsDefaultValue(
                        codes, new RuntimeException("Catalog service down"));

        assertThat(fallbackResponse.exists()).isTrue();
    }
}
