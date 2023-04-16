package com.example.catalogservice.services;

import com.example.catalogservice.model.response.InventoryDto;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "inventory-service", url = "${application.inventory-service-url}")
public interface InventoryServiceProxy {

    @GetMapping("/api/inventory/{productCode}")
    InventoryDto getInventoryByProductCode(@PathVariable String productCode);

    @GetMapping("/api/inventory/product")
    List<InventoryDto> getInventoryByProductCodes(@RequestParam List<String> codes);
}
