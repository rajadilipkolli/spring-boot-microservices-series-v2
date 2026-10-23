/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.common.dto.PagedResult;
import com.example.inventoryservice.inventory.InventoryAPI;
import com.example.inventoryservice.inventory.ProductManageService;
import com.example.inventoryservice.inventory.domain.models.InventoryRequest;
import com.example.inventoryservice.inventory.domain.models.InventoryResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class InventoryAPIImpl implements InventoryAPI {
    private final InventoryService inventoryService;
    private final InventoryMapper inventoryMapper;
    private final InventoryOrderManageServiceImpl orderManageService;
    private final ProductManageService productManageService;

    /** Creates the facade with inventory persistence, response mapping, and event handlers. */
    InventoryAPIImpl(
            InventoryService inventoryService,
            InventoryMapper inventoryMapper,
            InventoryOrderManageServiceImpl orderManageService,
            ProductManageService productManageService) {
        this.inventoryService = inventoryService;
        this.inventoryMapper = inventoryMapper;
        this.orderManageService = orderManageService;
        this.productManageService = productManageService;
    }

    /** Delegates the sorted inventory page lookup to the inventory service. */
    @Override
    public PagedResult<InventoryResponse> findAllInventories(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        return inventoryService.findAllInventories(pageNo, pageSize, sortBy, sortDir);
    }

    /** Maps inventory found by identifier to a response, preserving an absent result. */
    @Override
    public Optional<InventoryResponse> findInventoryById(Long id) {
        return inventoryService.findInventoryById(id).map(inventoryMapper::toResponse);
    }

    /** Maps inventory found by product code to a response, preserving an absent result. */
    @Override
    public Optional<InventoryResponse> findInventoryByProductCode(String productCode) {
        return inventoryService
                .findInventoryByProductCode(productCode)
                .map(inventoryMapper::toResponse);
    }

    /** Delegates the paged lookup restricted to the supplied product codes. */
    @Override
    public PagedResult<InventoryResponse> getInventoryByProductCodes(
            List<String> codes, int pageNo, int pageSize, String sortBy, String sortDir) {
        return inventoryService.getInventoryByProductCodes(
                codes, pageNo, pageSize, sortBy, sortDir);
    }

    /** Creates inventory in a write transaction and maps the saved entity to a response. */
    @Override
    @Transactional
    public InventoryResponse saveInventory(InventoryRequest inventoryRequest) {
        return inventoryMapper.toResponse(inventoryService.saveInventory(inventoryRequest));
    }

    /** Updates inventory by identifier in a write transaction, returning empty when absent. */
    @Override
    @Transactional
    public Optional<InventoryResponse> updateInventoryById(
            Long id, InventoryRequest inventoryRequest) {
        return inventoryService
                .updateInventoryById(id, inventoryRequest)
                .map(inventoryMapper::toResponse);
    }

    /** Updates inventory by product code in a write transaction, returning empty when absent. */
    @Override
    @Transactional
    public Optional<InventoryResponse> updateInventoryByProductCode(
            String productCode, InventoryRequest inventoryRequest) {
        return inventoryService
                .updateInventoryByProductCode(productCode, inventoryRequest)
                .map(inventoryMapper::toResponse);
    }

    /** Deletes inventory by identifier within a write transaction. */
    @Override
    @Transactional
    public void deleteInventoryById(Long id) {
        inventoryService.deleteInventoryById(id);
    }

    /** Runs the idempotent generated-batch update within a write transaction. */
    @Override
    @Transactional
    public void updateGeneratedInventory(String idempotencyKey, Integer batchSize) {
        inventoryService.updateGeneratedInventory(idempotencyKey, batchSize);
    }

    /**
     * Reserves order quantities and publishes the outcome through the transactional order service.
     */
    @Override
    @Transactional
    public void reserve(com.example.inventoryservice.inventory.domain.models.OrderDto orderDto) {
        orderManageService.reserve(orderDto);
    }

    /** Applies order confirmation or rollback stock changes within a write transaction. */
    @Override
    @Transactional
    public void confirm(com.example.inventoryservice.inventory.domain.models.OrderDto orderDto) {
        orderManageService.confirm(orderDto);
    }

    /** Handles product creation within a write transaction, ignoring existing product codes. */
    @Override
    @Transactional
    public void manage(com.example.inventoryservice.inventory.domain.models.ProductDto productDto) {
        productManageService.manage(productDto);
    }
}
