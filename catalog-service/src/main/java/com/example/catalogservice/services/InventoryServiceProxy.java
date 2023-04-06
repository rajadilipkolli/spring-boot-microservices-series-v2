package com.example.catalogservice.services;

import com.example.catalogservice.model.response.InventoryDto;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

@HttpExchange("/api/inventory")
public interface InventoryServiceProxy {

    @GetExchange("/{productCode}")
    InventoryDto getInventoryByProductCode(@PathVariable String productCode);

    @GetExchange("/product")
    List<InventoryDto> getInventoryByProductCodes(@RequestParam List<String> codes);
}
