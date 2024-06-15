package com.example.retailstore.webapp.clients.catalog;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

public interface CatalogServiceClient {

    @GetExchange("/catalog/api/products")
    PagedResult<ProductResponse> getProducts(@RequestParam int page);
}
