/***
<p>
    Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.orderservice.config.logging.Loggable;
import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;

// @HttpExchange("lb://catalog-service/")
// @HttpExchange("http://localhost:18080/catalog-service")
// @HttpExchange(url = "${application.catalog-service-url}")
@Loggable
public interface CatalogServiceProxy {

    @GetExchange("/api/catalog/exists")
    boolean productsExistsByCodes(@RequestParam List<String> productCodes);
}
