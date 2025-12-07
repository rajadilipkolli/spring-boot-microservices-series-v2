package com.example.retailstore.webapp.clients.catalog;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/catalog-service")
public interface CatalogServiceClient {

    @GetExchange("/api/catalog")
    String getProducts(@RequestParam int pageNo);

    @PostExchange("/api/catalog")
    ProductResponse createProduct(@RequestBody ProductRequest productRequest);
}
