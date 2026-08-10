/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.web.controllers;

import com.example.inventoryservice.model.request.InventoryRequest;
import com.example.inventoryservice.model.response.InventoryResponse;
import com.example.inventoryservice.model.response.PagedResult;
import com.example.inventoryservice.services.InventoryService;
import com.example.inventoryservice.utils.AppConstants;
import com.example.inventoryservice.utils.logging.Loggable;
import jakarta.validation.Valid;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

@RestController
@RequestMapping("/api/inventory")
@Loggable
@Validated
class InventoryController {

    private final InventoryService inventoryService;
    private final com.example.inventoryservice.mapper.InventoryMapper inventoryMapper;

    InventoryController(
            InventoryService inventoryService,
            com.example.inventoryservice.mapper.InventoryMapper inventoryMapper) {
        this.inventoryService = inventoryService;
        this.inventoryMapper = inventoryMapper;
    }

    @GetMapping
    PagedResult<InventoryResponse> getAllInventories(
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER, required = false)
                    int pageNo,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE, required = false)
                    int pageSize,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY, required = false)
                    String sortBy,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_DIRECTION, required = false)
                    String sortDir) {
        return inventoryService.findAllInventories(pageNo, pageSize, sortBy, sortDir);
    }

    @GetMapping("/{productCode}")
    // @Retry(name = "inventory-api", fallbackMethod = "hardcodedResponse")
    // @CircuitBreaker(name = "default", fallbackMethod = "hardcodedResponse")
    // @RateLimiter(name = "default")
    // @Bulkhead(name = "inventory-api")
    ResponseEntity<InventoryResponse> getInventoryByProductCode(
            @PathVariable String productCode, @RequestParam(required = false) Integer delay) {
        // If delay is specified, block for the requested seconds — used by tests to
        // simulate slow
        // responses
        if (delay != null && delay > 0) {
            try {
                TimeUnit.SECONDS.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return inventoryService
                .findInventoryByProductCode(productCode)
                .map(inventoryMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/product")
    ResponseEntity<PagedResult<InventoryResponse>> getInventoryByProductCodes(
            @RequestParam List<String> codes,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER, required = false)
                    int pageNo,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE, required = false)
                    int pageSize,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY, required = false)
                    String sortBy,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_DIRECTION, required = false)
                    String sortDir) {
        return ResponseEntity.ok(
                inventoryService.getInventoryByProductCodes(
                        codes, pageNo, pageSize, sortBy, sortDir));
    }

    @PostMapping("/generate")
    boolean updateInventoryWithRandomValue() {
        inventoryService.updateGeneratedInventory();
        return true;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    InventoryResponse createInventory(@RequestBody @Valid InventoryRequest inventoryRequest) {
        return inventoryMapper.toResponse(inventoryService.saveInventory(inventoryRequest));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<InventoryResponse> updateInventory(
            @PathVariable Long id, @RequestBody @Valid InventoryRequest inventoryRequest) {
        return inventoryService
                .updateInventoryById(id, inventoryRequest)
                .map(inventoryMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(value = "/product/{productCode}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<InventoryResponse> updateInventoryByProductCode(
            @PathVariable String productCode,
            @RequestBody @Valid InventoryRequest inventoryRequest) {
        if (inventoryRequest.productCode() != null
                && !inventoryRequest.productCode().equals(productCode)) {
            return ResponseEntity.badRequest().build();
        }
        return inventoryService
                .updateInventoryByProductCode(productCode, inventoryRequest)
                .map(inventoryMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<InventoryResponse> deleteInventory(@PathVariable Long id) {
        return inventoryService
                .findInventoryById(id)
                .map(
                        inventory -> {
                            inventoryService.deleteInventoryById(id);
                            return ResponseEntity.ok(inventoryMapper.toResponse(inventory));
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
