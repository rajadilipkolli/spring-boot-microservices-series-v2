package com.example.inventoryservice.services;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.entities.Order;
import com.example.inventoryservice.repositories.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderManageService {

  private static final String SOURCE = "stock";
  private static final Logger LOG = LoggerFactory.getLogger(OrderManageService.class);
  private final InventoryRepository repository;
  private final KafkaTemplate<Long, Order> template;

  public OrderManageService(InventoryRepository repository, KafkaTemplate<Long, Order> template) {
    this.repository = repository;
    this.template = template;
  }

  public void reserve(Order order) {
    Inventory product = repository.findById(order.getItems().get(0).getProductId()).orElseThrow();
    LOG.info("Found: {}", product);
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
      template.send("stock-orders", order.getId(), order);
      LOG.info("Sent: {}", order);
    }
  }

  public void confirm(Order order) {
    Inventory product = repository.findById(order.getItems().get(0).getProductId()).orElseThrow();
    LOG.info("Found: {}", product);
    int productCount = order.getItems().get(0).getQuantity();
    if ("CONFIRMED".equals(order.getStatus())) {
      product.setReservedItems(product.getReservedItems() - productCount);
      repository.save(product);
    } else if ("ROLLBACK".equals(order.getStatus()) && !SOURCE.equals(order.getSource())) {
      product.setReservedItems(product.getReservedItems() - productCount);
      product.setAvailableQuantity(product.getAvailableQuantity() + productCount);
      repository.save(product);
    }
  }
}
