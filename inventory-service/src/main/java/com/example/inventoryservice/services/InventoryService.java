/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
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
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
@Loggable
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    private final InventoryMapper inventoryMapper;

    private final InventoryJOOQRepository inventoryJOOQRepository;

    @Transactional(readOnly = true)
    public PagedResult<Inventory> findAllInventories(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        log.info(
                "Fetching findAllInventories for pageNo {} with pageSize {}, sorting BY {} {}",
                pageNo,
                pageSize,
                sortBy,
                sortDir);

        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        return new PagedResult<>(inventoryJOOQRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    public Optional<Inventory> findInventoryById(Long id) {
        return inventoryJOOQRepository.findById(id);
    }

    public Inventory saveInventory(InventoryRequest inventoryRequest) {

        Inventory inventory = this.inventoryMapper.toEntity(inventoryRequest);
        return inventoryRepository.save(inventory);
    }

    public void deleteInventoryById(Long id) {
        inventoryRepository.deleteById(id);
    }

    public Inventory updateInventory(Inventory inventory, InventoryRequest inventoryRequest) {
        this.inventoryMapper.updateInventoryFromRequest(inventoryRequest, inventory);
        return inventoryRepository.save(inventory);
    }

    @Transactional(readOnly = true)
    public Optional<Inventory> findInventoryByProductCode(String productCode) {
        return this.inventoryJOOQRepository.findByProductCode(productCode);
    }

    @Transactional(readOnly = true)
    public List<Inventory> getInventoryByProductCodes(List<String> productCodes) {
        return this.inventoryJOOQRepository.findByProductCodeIn(productCodes);
    }
}
