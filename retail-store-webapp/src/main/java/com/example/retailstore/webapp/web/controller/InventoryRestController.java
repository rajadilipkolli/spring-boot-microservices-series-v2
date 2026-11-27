package com.example.retailstore.webapp.web.controller;

import com.example.common.dto.PagedResult;
import com.example.common.logging.LogSanitizer;
import com.example.retailstore.webapp.clients.inventory.InventoryResponse;
import com.example.retailstore.webapp.clients.inventory.InventoryServiceClient;
import com.example.retailstore.webapp.exception.InvalidRequestException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class InventoryRestController {

    private static final Logger log = LoggerFactory.getLogger(InventoryRestController.class);

    private final InventoryServiceClient inventoryServiceClient;

    public InventoryRestController(InventoryServiceClient inventoryServiceClient) {
        this.inventoryServiceClient = inventoryServiceClient;
    }

    @GetMapping(value = "/api/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
    PagedResult<InventoryResponse> inventories(@RequestParam(defaultValue = "0") int page) {
        log.info("Fetching inventories for page: {}", LogSanitizer.sanitizeForLog(String.valueOf(page)));
        try {
            return inventoryServiceClient.getInventories(page);
        } catch (Exception e) {
            log.error("Error fetching inventories: {}", LogSanitizer.sanitizeException(e));
            throw new InvalidRequestException("Failed to fetch inventories. Please try again later.");
        }
    }

    @PutMapping(value = "/inventory", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    InventoryResponse updateInventory(@Valid @RequestBody InventoryResponse inventoryResponse) {
        log.debug("Input Received :{}", LogSanitizer.sanitizeForLog(String.valueOf(inventoryResponse)));
        try {
            if (inventoryResponse.id() == null) {
                throw new InvalidRequestException("Inventory ID cannot be null");
            }
            return inventoryServiceClient.updateInventory(
                    inventoryResponse.id(), inventoryResponse.createInventoryUpdateRequest());
        } catch (InvalidRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error(
                    "Error updating inventory {}: {}",
                    LogSanitizer.sanitizeForLog(String.valueOf(inventoryResponse.id())),
                    LogSanitizer.sanitizeException(e));
            throw new InvalidRequestException("Failed to update inventory. Please try again later.");
        }
    }
}
