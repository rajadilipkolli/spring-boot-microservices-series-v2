/* Licensed under Apache-2.0 2022 */
package com.example.orderservice.services;

import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

// @HttpExchange("lb://catalog-service/")
@HttpExchange("http://localhost:18080/catalog-service/api/catalog")
public interface CatalogServiceProxy {

    @GetExchange("/exists/{productIds}")
    Boolean productsExists(@PathVariable List<String> productIds);
}
