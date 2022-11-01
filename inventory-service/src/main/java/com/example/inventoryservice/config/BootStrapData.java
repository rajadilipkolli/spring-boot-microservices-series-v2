/* Licensed under Apache-2.0 2022 */
package com.example.inventoryservice.config;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.repositories.InventoryRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class BootStrapData {

    private final InventoryRepository inventoryRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("start data initialization...");
        this.inventoryRepository.deleteAllInBatch();
        Random r = new Random();
        List<Inventory> inventoryList = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            int count = r.nextInt(1000);
            Inventory inventory = new Inventory(null, "Product" + i, count, 0);
            inventoryList.add(inventory);
        }
        inventoryRepository.saveAll(inventoryList);
    }
}
