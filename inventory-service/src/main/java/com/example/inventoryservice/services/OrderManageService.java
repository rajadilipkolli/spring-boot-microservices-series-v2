package com.example.inventoryservice.services;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.entities.Order;
import com.example.inventoryservice.repositories.InventoryRepository;
import com.example.orderservice.dtos.OrderDto;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderManageService {

  private static final String SOURCE = "stock";
  private static final String ROLLBACK = "ROLLBACK";
  
  private final InventoryRepository repository;
  private final KafkaTemplate<Long, OrderDto> template;

  public void reserve(OrderDto order) {
    Inventory product = repository.findById(order.getItems().get(0).getProductId()).orElseThrow();
    log.info("Found: {}", product);
    if ("NEW".equals(order.getStatus())) {
      int productCount = order.getItems().get(0).getQuantity();
      if (productCount < product.getAvailableQuantity()) {
        product.setReservedItems(product.getReservedItems() + productCount);
        product.setAvailableQuantity(product.getAvailableQuantity() - productCount);
        order.setStatus("ACCEPT");
        repository.save(product);
      } else {
        order.setStatus("REJECT");
      }
      template.send("stock-orders", order.getOrderId(), order);
      log.info("Sent: {}", order);
    }
  }

  public void confirm(OrderDto order) {
    Inventory product = repository.findById(order.getItems().get(0).getProductId()).orElseThrow();
    log.info("Found: {}", product);
    int productCount = order.getItems().get(0).getQuantity();
    if ("CONFIRMED".equals(order.getStatus())) {
      product.setReservedItems(product.getReservedItems() - productCount);
      repository.save(product);
    } else if (ROLLBACK.equals(order.getStatus()) && !(SOURCE.equalsIgnoreCase(order.getSource()))) {
      product.setReservedItems(product.getReservedItems() - productCount);
      product.setAvailableQuantity(product.getAvailableQuantity() + productCount);
      repository.save(product);
    }
  }
}
