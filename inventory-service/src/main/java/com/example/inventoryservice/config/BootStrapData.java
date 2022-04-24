package com.example.inventoryservice.config;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.repositories.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Random;

@Component
@Slf4j
@RequiredArgsConstructor
public class BootStrapData {

  private final InventoryRepository inventoryRepository;

  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    log.info("start data initialization...");
    this.inventoryRepository.deleteAll();
    Random r = new Random();
    for (int i = 0; i < 1000; i++) {
      int count = r.nextInt(1000);
      Inventory p = new Inventory(null, "Product" + i, count, 0);
      inventoryRepository.save(p);
    }
  }
}
