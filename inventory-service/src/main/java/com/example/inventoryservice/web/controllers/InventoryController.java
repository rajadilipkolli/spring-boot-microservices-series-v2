/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice.web.controllers;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.model.response.PagedResult;
import com.example.inventoryservice.model.response.request.InventoryRequest;
import com.example.inventoryservice.services.InventoryService;
import com.example.inventoryservice.utils.AppConstants;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public PagedResult<Inventory> getAllInventories(
            @RequestParam(
                            value = "pageNo",
                            defaultValue = AppConstants.DEFAULT_PAGE_NUMBER,
                            required = false)
                    int pageNo,
            @RequestParam(
                            value = "pageSize",
                            defaultValue = AppConstants.DEFAULT_PAGE_SIZE,
                            required = false)
                    int pageSize,
            @RequestParam(
                            value = "sortBy",
                            defaultValue = AppConstants.DEFAULT_SORT_BY,
                            required = false)
                    String sortBy,
            @RequestParam(
                            value = "sortDir",
                            defaultValue = AppConstants.DEFAULT_SORT_DIRECTION,
                            required = false)
                    String sortDir) {
        return inventoryService.findAllInventories(pageNo, pageSize, sortBy, sortDir);
    }

    @GetMapping("/{productCode}")
    // @Retry(name = "inventory-api", fallbackMethod = "hardcodedResponse")
    // @CircuitBreaker(name = "default", fallbackMethod = "hardcodedResponse")
    // @RateLimiter(name = "default")
    // @Bulkhead(name = "inventory-api")
    public ResponseEntity<Inventory> getInventoryByProductCode(@PathVariable String productCode) {
        return inventoryService
                .findInventoryByProductCode(productCode)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/product")
    public ResponseEntity<List<Inventory>> getInventoryByProductCodes(
            @RequestParam List<String> codes) {
        return ResponseEntity.ok(inventoryService.getInventoryByProductCodes(codes));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Inventory createInventory(@RequestBody @Validated InventoryRequest inventoryRequest) {
        return inventoryService.saveInventory(inventoryRequest);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Inventory> updateInventory(
            @PathVariable Long id, @RequestBody @Validated InventoryRequest inventoryRequest) {
        return inventoryService
                .findInventoryById(id)
                .map(
                        inventoryFromDB -> {
                            return ResponseEntity.ok(
                                    inventoryService.updateInventory(
                                            inventoryFromDB, inventoryRequest));
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Inventory> deleteInventory(@PathVariable Long id) {
        return inventoryService
                .findInventoryById(id)
                .map(
                        inventory -> {
                            inventoryService.deleteInventoryById(id);
                            return ResponseEntity.ok(inventory);
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
