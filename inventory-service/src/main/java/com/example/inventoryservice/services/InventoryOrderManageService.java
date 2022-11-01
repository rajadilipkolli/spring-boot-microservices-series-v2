/* Licensed under Apache-2.0 2022 */
package com.example.inventoryservice.services;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.repositories.InventoryRepository;
import com.example.inventoryservice.utils.AppConstants;
import com.example.orderservice.dtos.OrderDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryOrderManageService {

    private final InventoryRepository inventoryRepository;
    private final KafkaTemplate<Long, OrderDto> kafkaTemplate;

    public void reserve(OrderDto orderDto) {
        Inventory product =
                inventoryRepository
                        .findByProductCode(orderDto.getItems().get(0).getProductId())
                        .orElseThrow();
        log.info("Found: {}", product);
        if ("NEW".equals(orderDto.getStatus())) {
            int productCount = orderDto.getItems().get(0).getQuantity();
            if (productCount < product.getAvailableQuantity()) {
                product.setReservedItems(product.getReservedItems() + productCount);
                product.setAvailableQuantity(product.getAvailableQuantity() - productCount);
                orderDto.setStatus("ACCEPT");
                inventoryRepository.save(product);
            } else {
                orderDto.setStatus("REJECT");
            }
            kafkaTemplate.send(AppConstants.STOCK_ORDERS_TOPIC, orderDto.getOrderId(), orderDto);
            log.info("Sent: {}", orderDto);
        }
    }

    public void confirm(OrderDto orderDto) {
        Inventory product =
                inventoryRepository
                        .findByProductCode(orderDto.getItems().get(0).getProductId())
                        .orElseThrow();
        log.info("Found: {}", product);
        int productCount = orderDto.getItems().get(0).getQuantity();
        if ("CONFIRMED".equals(orderDto.getStatus())) {
            product.setReservedItems(product.getReservedItems() - productCount);
            inventoryRepository.save(product);
        } else if (AppConstants.ROLLBACK.equals(orderDto.getStatus())
                && !(AppConstants.SOURCE.equalsIgnoreCase(orderDto.getSource()))) {
            product.setReservedItems(product.getReservedItems() - productCount);
            product.setAvailableQuantity(product.getAvailableQuantity() + productCount);
            inventoryRepository.save(product);
        }
    }
}
