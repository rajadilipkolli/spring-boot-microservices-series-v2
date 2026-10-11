/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.controllers;

import com.example.inventoryservice.inventory.InventoryAPI;
import com.example.inventoryservice.inventory.domain.models.InventoryRequest;
import com.example.inventoryservice.inventory.domain.models.InventoryResponse;
import com.example.inventoryservice.inventory.domain.models.PagedResult;
import com.example.inventoryservice.inventory.utils.AppConstants;
import com.example.inventoryservice.inventory.utils.logging.Loggable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
@Loggable
@Validated
class InventoryController {

    private final InventoryAPI inventoryAPI;

    InventoryController(InventoryAPI inventoryAPI) {
        this.inventoryAPI = inventoryAPI;
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
        return inventoryAPI.findAllInventories(pageNo, pageSize, sortBy, sortDir);
    }

    @GetMapping("/{productCode}")
    ResponseEntity<InventoryResponse> getInventoryByProductCode(
            @PathVariable String productCode, @RequestParam(required = false) Integer delay) {
        if (delay != null && delay > 0) {
            try {
                TimeUnit.SECONDS.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return inventoryAPI
                .findInventoryByProductCode(productCode)
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
                inventoryAPI.getInventoryByProductCodes(codes, pageNo, pageSize, sortBy, sortDir));
    }

    @PostMapping("/generate")
    boolean updateInventoryWithRandomValue(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestParam(required = false) @Min(1) @Max(10000) Integer batchSize) {
        inventoryAPI.updateGeneratedInventory(idempotencyKey, batchSize);
        return true;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    InventoryResponse createInventory(@RequestBody @Valid InventoryRequest inventoryRequest) {
        return inventoryAPI.saveInventory(inventoryRequest);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<InventoryResponse> updateInventory(
            @PathVariable Long id, @RequestBody @Valid InventoryRequest inventoryRequest) {
        return inventoryAPI
                .updateInventoryById(id, inventoryRequest)
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
        return inventoryAPI
                .updateInventoryByProductCode(productCode, inventoryRequest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<InventoryResponse> deleteInventory(@PathVariable Long id) {
        return inventoryAPI
                .findInventoryById(id)
                .map(
                        inventory -> {
                            inventoryAPI.deleteInventoryById(id);
                            return ResponseEntity.ok(inventory);
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
