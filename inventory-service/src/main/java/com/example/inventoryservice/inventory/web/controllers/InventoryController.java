/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.controllers;

import com.example.common.dto.PagedResult;
import com.example.common.logging.Loggable;
import com.example.inventoryservice.inventory.InventoryAPI;
import com.example.inventoryservice.inventory.domain.models.InventoryRequest;
import com.example.inventoryservice.inventory.domain.models.InventoryResponse;
import com.example.inventoryservice.inventory.utils.AppConstants;
import com.example.inventoryservice.inventory.web.api.InventoryApi;
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
class InventoryController implements InventoryApi {

    private final InventoryAPI inventoryAPI;

    /** Creates the HTTP adapter for the inventory module API. */
    InventoryController(InventoryAPI inventoryAPI) {
        this.inventoryAPI = inventoryAPI;
    }

    /** Returns inventory records using the requested pagination and sort options. */
    @GetMapping
    @Override
    public PagedResult<InventoryResponse> getAllInventories(
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

    /**
     * Returns inventory by product code, or HTTP 404 when it is absent.
     *
     * <p>A positive optional delay pauses the lookup for the supplied number of seconds.
     */
    @GetMapping("/{productCode}")
    @Override
    public ResponseEntity<InventoryResponse> getInventoryByProductCode(
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

    /** Returns HTTP 200 with a sorted page of inventory matching the supplied product codes. */
    @GetMapping("/product")
    @Override
    public ResponseEntity<PagedResult<InventoryResponse>> getInventoryByProductCodes(
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

    /** Updates a generated batch using the idempotency key and returns true on completion. */
    @PostMapping("/generate")
    @Override
    public boolean updateInventoryWithRandomValue(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestParam(required = false) @Min(1) @Max(10000) Integer batchSize) {
        inventoryAPI.updateGeneratedInventory(idempotencyKey, batchSize);
        return true;
    }

    /** Creates inventory from a validated request and returns its representation with HTTP 201. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public InventoryResponse createInventory(
            @RequestBody @Valid InventoryRequest inventoryRequest) {
        return inventoryAPI.saveInventory(inventoryRequest);
    }

    /** Updates inventory by identifier, returning HTTP 404 when it is absent. */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Override
    public ResponseEntity<InventoryResponse> updateInventory(
            @PathVariable Long id, @RequestBody @Valid InventoryRequest inventoryRequest) {
        return inventoryAPI
                .updateInventoryById(id, inventoryRequest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Updates inventory by product code, returning HTTP 404 when it is absent.
     *
     * <p>A request product code that differs from the path is rejected with HTTP 400.
     */
    @PutMapping(value = "/product/{productCode}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Override
    public ResponseEntity<InventoryResponse> updateInventoryByProductCode(
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

    /**
     * Deletes existing inventory and returns its previous representation, or HTTP 404 if absent.
     */
    @DeleteMapping("/{id}")
    @Override
    public ResponseEntity<InventoryResponse> deleteInventory(@PathVariable Long id) {
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
