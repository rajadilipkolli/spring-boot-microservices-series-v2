/***
<p>
    Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice.services;

import com.example.common.dtos.ProductDto;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.repositories.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductManageService {

    private final InventoryRepository inventoryRepository;

    public void manage(ProductDto productDto) {
        Inventory inventory = new Inventory().setProductCode(productDto.code());
        this.inventoryRepository.save(inventory);
    }
}
