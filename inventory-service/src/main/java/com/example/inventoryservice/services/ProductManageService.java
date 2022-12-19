/* Licensed under Apache-2.0 2022 */
package com.example.inventoryservice.services;

import com.example.inventoryservice.dtos.ProductDto;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.repositories.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductManageService {

    private final InventoryRepository inventoryRepository;

    public void manage(ProductDto product) {
        Inventory inventory = new Inventory();
        inventory.setProductCode(product.getCode());
        inventory.setAvailableQuantity(0);
        this.inventoryRepository.save(inventory);
    }
}
