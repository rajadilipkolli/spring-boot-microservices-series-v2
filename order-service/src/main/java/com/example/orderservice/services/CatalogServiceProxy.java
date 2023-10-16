/***
<p>
    Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

// @HttpExchange("lb://catalog-service/")
// @HttpExchange("http://localhost:18080/catalog-service")
// @HttpExchange(url = "${application.catalog-service-url}")
public interface CatalogServiceProxy {

    @GetExchange("/api/catalog/exists")
    boolean productsExistsByCodes(@RequestParam(name = "productCodes") List<String> productCodes);
}
