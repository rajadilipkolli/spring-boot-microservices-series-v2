/* (C)2022 */
package com.example.inventoryservice.services;

import com.example.inventoryservice.dtos.InventoryDto;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.mapper.InventoryMapper;
import com.example.inventoryservice.repositories.InventoryRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    private final InventoryMapper inventoryMapper;

    @Autowired
    public InventoryService(
            InventoryRepository inventoryRepository, InventoryMapper inventoryMapper) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryMapper = inventoryMapper;
    }

    public List<Inventory> findAllInventories() {
        return inventoryRepository.findAll();
    }

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

    public Optional<Inventory> findInventoryByProductCode(String productCode) {
        return this.inventoryRepository.findByProductCode(productCode);
    }
}
