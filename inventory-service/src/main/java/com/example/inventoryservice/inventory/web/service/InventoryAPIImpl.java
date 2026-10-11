/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.inventoryservice.inventory.InventoryAPI;
import com.example.inventoryservice.inventory.ProductManageService;
import com.example.inventoryservice.inventory.domain.models.InventoryRequest;
import com.example.inventoryservice.inventory.domain.models.InventoryResponse;
import com.example.inventoryservice.inventory.domain.models.PagedResult;
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

    @Override
    public PagedResult<InventoryResponse> findAllInventories(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        return inventoryService.findAllInventories(pageNo, pageSize, sortBy, sortDir);
    }

    @Override
    public Optional<InventoryResponse> findInventoryById(Long id) {
        return inventoryService.findInventoryById(id).map(inventoryMapper::toResponse);
    }

    @Override
    public Optional<InventoryResponse> findInventoryByProductCode(String productCode) {
        return inventoryService
                .findInventoryByProductCode(productCode)
                .map(inventoryMapper::toResponse);
    }

    @Override
    public PagedResult<InventoryResponse> getInventoryByProductCodes(
            List<String> codes, int pageNo, int pageSize, String sortBy, String sortDir) {
        return inventoryService.getInventoryByProductCodes(
                codes, pageNo, pageSize, sortBy, sortDir);
    }

    @Override
    @Transactional
    public InventoryResponse saveInventory(InventoryRequest inventoryRequest) {
        return inventoryMapper.toResponse(inventoryService.saveInventory(inventoryRequest));
    }

    @Override
    @Transactional
    public Optional<InventoryResponse> updateInventoryById(
            Long id, InventoryRequest inventoryRequest) {
        return inventoryService
                .updateInventoryById(id, inventoryRequest)
                .map(inventoryMapper::toResponse);
    }

    @Override
    @Transactional
    public Optional<InventoryResponse> updateInventoryByProductCode(
            String productCode, InventoryRequest inventoryRequest) {
        return inventoryService
                .updateInventoryByProductCode(productCode, inventoryRequest)
                .map(inventoryMapper::toResponse);
    }

    @Override
    @Transactional
    public void deleteInventoryById(Long id) {
        inventoryService.deleteInventoryById(id);
    }

    @Override
    @Transactional
    public void updateGeneratedInventory(String idempotencyKey, Integer batchSize) {
        inventoryService.updateGeneratedInventory(idempotencyKey, batchSize);
    }

    @Override
    public void reserve(com.example.inventoryservice.inventory.domain.models.OrderDto orderDto) {
        orderManageService.reserve(orderDto);
    }

    @Override
    public void confirm(com.example.inventoryservice.inventory.domain.models.OrderDto orderDto) {
        orderManageService.confirm(orderDto);
    }

    @Override
    public void manage(com.example.inventoryservice.inventory.domain.models.ProductDto productDto) {
        productManageService.manage(productDto);
    }
}
