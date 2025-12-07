/***
<p>
    Licensed under MIT License Copyright (c) 2022-2024 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.orderservice.config.logging.Loggable;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

// @HttpExchange("lb://catalog-service/")
// @HttpExchange("http://localhost:18080/catalog-service")
@HttpExchange(
        url = "${application.catalog-service-url}",
        accept = MediaType.APPLICATION_JSON_VALUE,
        contentType = MediaType.APPLICATION_JSON_VALUE)
@Loggable
public interface CatalogServiceProxy {

    @GetExchange("/api/catalog/exists")
    boolean productsExistsByCodes(@RequestParam List<String> productCodes);
}
