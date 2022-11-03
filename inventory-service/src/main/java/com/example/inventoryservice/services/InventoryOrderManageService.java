/* Licensed under Apache-2.0 2022 */
package com.example.inventoryservice.services;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.repositories.InventoryRepository;
import com.example.inventoryservice.utils.AppConstants;
import com.example.orderservice.dtos.OrderDto;
import com.example.orderservice.dtos.OrderItemDto;
import java.util.ArrayList;
import java.util.List;
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
        List<String> productCodeList =
                orderDto.getItems().stream().map(OrderItemDto::getProductId).toList();
        List<Inventory> inventoryList = inventoryRepository.findByProductCodeIn(productCodeList);
        if (inventoryList.size() == productCodeList.size()) {
            log.info("All Products Exists");
            if ("NEW".equals(orderDto.getStatus())) {
                List<Inventory> persistInventoryList = new ArrayList<>();

                outerLoopBreakVariable:
                for (OrderItemDto orderItemDto : orderDto.getItems()) {
                    for (Inventory inventory : inventoryList) {
                        if (inventory.getProductCode().equals(orderItemDto.getProductId())) {
                            int productCount = orderItemDto.getQuantity();
                            if (productCount < inventory.getAvailableQuantity()) {
                                inventory.setReservedItems(
                                        inventory.getReservedItems() + productCount);
                                inventory.setAvailableQuantity(
                                        inventory.getAvailableQuantity() - productCount);
                                persistInventoryList.add(inventory);
                            } else {
                                break outerLoopBreakVariable;
                            }
                            break;
                        }
                    }
                }
                if (inventoryList.size() == persistInventoryList.size()) {
                    orderDto.setStatus("ACCEPT");
                    inventoryRepository.saveAll(persistInventoryList);
                } else {
                    orderDto.setStatus("REJECT");
                }
                kafkaTemplate.send(
                        AppConstants.STOCK_ORDERS_TOPIC, orderDto.getOrderId(), orderDto);
                log.info("Sent: {}", orderDto);
            }
        } else {
            log.error("Not all products requested exists");
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
