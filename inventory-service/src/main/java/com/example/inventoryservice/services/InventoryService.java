/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.services;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.exception.ProductAlreadyExistsException;
import com.example.inventoryservice.mapper.InventoryMapper;
import com.example.inventoryservice.model.request.InventoryRequest;
import com.example.inventoryservice.model.response.InventoryResponse;
import com.example.inventoryservice.model.response.PagedResult;
import com.example.inventoryservice.repositories.InventoryJOOQRepository;
import com.example.inventoryservice.repositories.InventoryRepository;
import com.example.inventoryservice.utils.logging.Loggable;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Loggable
public class InventoryService {

    private static final SecureRandom RAND = new SecureRandom();
    private final InventoryRepository inventoryRepository;

    private final InventoryMapper inventoryMapper;

    private final InventoryJOOQRepository inventoryJOOQRepository;

    private final InventoryService self;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryMapper inventoryMapper,
            InventoryJOOQRepository inventoryJOOQRepository,
            @Lazy InventoryService self) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryMapper = inventoryMapper;
        this.inventoryJOOQRepository = inventoryJOOQRepository;
        this.self = self;
    }

    public PagedResult<InventoryResponse> findAllInventories(
            int pageNo, int pageSize, String sortBy, String sortDir) {

        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<InventoryResponse> page =
                inventoryJOOQRepository.findAll(pageable).map(inventoryMapper::toResponse);
        return new PagedResult<>(page);
    }

    public Optional<Inventory> findInventoryById(Long id) {
        return inventoryRepository.findById(id);
    }

    @Transactional
    public Inventory saveInventory(InventoryRequest inventoryRequest) {
        if (inventoryJOOQRepository.existsByProductCode(inventoryRequest.productCode())) {
            throw new ProductAlreadyExistsException(inventoryRequest.productCode());
        }
        Inventory inventory = this.inventoryMapper.toEntity(inventoryRequest);
        try {
            return inventoryRepository.save(inventory);
        } catch (DataIntegrityViolationException ex) {
            if (inventoryJOOQRepository.existsByProductCode(inventoryRequest.productCode())) {
                throw new ProductAlreadyExistsException(inventoryRequest.productCode());
            }
            throw ex;
        }
    }

    @Transactional
    public void deleteInventoryById(Long id) {
        inventoryRepository.deleteById(id);
    }

    @Transactional
    public Inventory updateInventory(Inventory inventory, InventoryRequest inventoryRequest) {
        this.inventoryMapper.updateInventoryFromRequest(inventoryRequest, inventory);
        return inventoryRepository.save(inventory);
    }

    public Optional<Inventory> findInventoryByProductCode(String productCode) {
        return this.inventoryRepository.findByProductCode(productCode);
    }

    public PagedResult<InventoryResponse> getInventoryByProductCodes(
            List<String> productCodes, int pageNo, int pageSize, String sortBy, String sortDir) {
        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<InventoryResponse> page =
                this.inventoryJOOQRepository
                        .findByProductCodeIn(productCodes, pageable)
                        .map(inventoryMapper::toResponse);
        return new PagedResult<>(page);
    }

    public void updateGeneratedInventory() {
        IntStream.rangeClosed(0, 100)
                .forEach(
                        operand -> {
                            try {
                                int randomQuantity = RAND.nextInt(10_000) + 1;
                                Optional<Inventory> inventoryByProductCode =
                                        findInventoryByProductCode("ProductCode" + operand);
                                inventoryByProductCode.ifPresent(
                                        inventoryFromDB ->
                                                self.updateInventory(
                                                        inventoryFromDB,
                                                        new InventoryRequest(
                                                                "ProductCode" + operand,
                                                                randomQuantity)));
                            } catch (OptimisticLockingFailureException e) {
                                // Ignore optimistic locking failures when concurrently updating
                                // random inventory
                            }
                        });
    }

    @Transactional
    public Optional<Inventory> updateInventoryById(Long id, InventoryRequest inventoryRequest) {
        return findInventoryById(id)
                .map(inventoryFromDB -> self.updateInventory(inventoryFromDB, inventoryRequest));
    }

    @Transactional
    public Optional<Inventory> updateInventoryByProductCode(
            String productCode, InventoryRequest inventoryRequest) {
        return findInventoryByProductCode(productCode)
                .map(inventoryFromDB -> self.updateInventory(inventoryFromDB, inventoryRequest));
    }
}
