package com.example.retailstore.webapp.clients.catalog;

import com.example.retailstore.webapp.clients.PagedResult;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

public interface CatalogServiceClient {

    @GetExchange("/api/catalog")
    PagedResult<ProductResponse> getProducts(@RequestParam int pageNo);
}
