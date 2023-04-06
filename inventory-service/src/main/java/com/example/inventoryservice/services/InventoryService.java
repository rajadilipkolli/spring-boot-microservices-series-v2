/* Licensed under Apache-2.0 2021-2023 */
package com.example.inventoryservice.services;

import com.example.inventoryservice.dtos.InventoryDto;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.mapper.InventoryMapper;
import com.example.inventoryservice.model.response.PagedResult;
import com.example.inventoryservice.repositories.InventoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    private final InventoryMapper inventoryMapper;

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
        return new PagedResult<>(inventoryRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    public Optional<Inventory> findInventoryById(Long id) {
        return inventoryRepository.findById(id);
    }

    public Inventory saveInventory(InventoryDto inventoryDto) {

        Inventory inventory = this.inventoryMapper.toEntity(inventoryDto);
        return inventoryRepository.save(inventory);
    }

    public void deleteInventoryById(Long id) {
        inventoryRepository.deleteById(id);
    }

    public Inventory updateInventory(Inventory inventory) {
        return inventoryRepository.save(inventory);
    }

    @Transactional(readOnly = true)
    public Optional<Inventory> findInventoryByProductCode(String productCode) {
        return this.inventoryRepository.findByProductCode(productCode);
    }

    @Transactional(readOnly = true)
    public List<Inventory> getInventoryByProductCodes(List<String> productCodes) {
        return this.inventoryRepository.findByProductCodeIn(productCodes);
    }
}
