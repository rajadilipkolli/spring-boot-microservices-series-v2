/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice.services;

import com.example.inventoryservice.config.logging.Loggable;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.mapper.InventoryMapper;
import com.example.inventoryservice.model.request.InventoryRequest;
import com.example.inventoryservice.model.response.PagedResult;
import com.example.inventoryservice.repositories.InventoryJOOQRepository;
import com.example.inventoryservice.repositories.InventoryRepository;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@Loggable
public class InventoryService {

    private static final SecureRandom RAND = new SecureRandom();
    private final InventoryRepository inventoryRepository;

    private final InventoryMapper inventoryMapper;

    private final InventoryJOOQRepository inventoryJOOQRepository;

    public InventoryService(
            InventoryRepository inventoryRepository,
            InventoryMapper inventoryMapper,
            InventoryJOOQRepository inventoryJOOQRepository) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryMapper = inventoryMapper;
        this.inventoryJOOQRepository = inventoryJOOQRepository;
    }

    public PagedResult<Inventory> findAllInventories(
            int pageNo, int pageSize, String sortBy, String sortDir) {

        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        return new PagedResult<>(inventoryJOOQRepository.findAll(pageable));
    }

    public Optional<Inventory> findInventoryById(Long id) {
        return inventoryJOOQRepository.findById(id);
    }

    @Transactional
    public Inventory saveInventory(InventoryRequest inventoryRequest) {
        Inventory inventory = this.inventoryMapper.toEntity(inventoryRequest);
        return inventoryRepository.save(inventory);
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
        return this.inventoryJOOQRepository.findByProductCode(productCode);
    }

    public List<Inventory> getInventoryByProductCodes(List<String> productCodes) {
        return this.inventoryJOOQRepository.findByProductCodeIn(productCodes);
    }

    @Transactional
    public void updateGeneratedInventory() {
        IntStream.rangeClosed(0, 100)
                .forEach(
                        operand -> {
                            int randomQuantity = RAND.nextInt(10_000) + 1;
                            Optional<Inventory> inventoryByProductCode =
                                    findInventoryByProductCode("ProductCode" + operand);
                            inventoryByProductCode.ifPresent(
                                    inventoryFromDB ->
                                            updateInventory(
                                                    inventoryFromDB,
                                                    new InventoryRequest(
                                                            "ProductCode" + operand,
                                                            randomQuantity)));
                        });
    }

    @Transactional
    public Optional<Inventory> updateInventoryById(Long id, InventoryRequest inventoryRequest) {
        return findInventoryById(id)
                .map(inventoryFromDB -> updateInventory(inventoryFromDB, inventoryRequest));
    }
}
