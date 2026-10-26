package com.example.retailstore.webapp.clients.catalog;

import com.example.common.dto.PagedResult;
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/catalog-service")
public interface CatalogServiceClient {

    @GetExchange("/api/catalog")
    PagedResult<ProductResponse> getProducts(@RequestParam int pageNo);

    @ClientRegistrationId("retailstore-webapp")
    @PostExchange("/api/catalog")
    ProductResponse createProduct(@RequestBody ProductRequest productRequest);
}
