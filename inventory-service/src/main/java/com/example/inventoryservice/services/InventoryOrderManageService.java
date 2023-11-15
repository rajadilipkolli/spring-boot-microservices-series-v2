/***
<p>
    Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice.services;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.repositories.InventoryJOOQRepository;
import com.example.inventoryservice.repositories.InventoryRepository;
import com.example.inventoryservice.utils.AppConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryOrderManageService {

    private final InventoryRepository inventoryRepository;
    private final InventoryJOOQRepository inventoryJOOQRepository;
    private final KafkaTemplate<Long, OrderDto> kafkaTemplate;

    public void reserve(OrderDto orderDto) {
        log.info("Reserving Order in Inventory Service {}", orderDto);
        // Check if order status is not NEW
        if (!"NEW".equals(orderDto.getStatus())) {
            log.error("Order status is not NEW, Hence Ignoring OrderID :{}", orderDto.getOrderId());
            return;
        }
        List<String> productCodeList =
                orderDto.getItems().stream().map(OrderItemDto::getProductId).toList();

        List<Inventory> inventoryListFromDB =
                inventoryJOOQRepository.findByProductCodeIn(productCodeList);

        if (inventoryListFromDB.size() != productCodeList.size()) {
            log.error(
                    "Not all products requested exist, Hence Ignoring OrderID : {}",
                    orderDto.getOrderId());
            return;
        }

        Map<String, Inventory> inventoryMap = new HashMap<>();
        inventoryListFromDB.forEach(
                inventory -> inventoryMap.put(inventory.getProductCode(), inventory));

        List<Inventory> updatedInventoryList = new ArrayList<>();

        for (OrderItemDto orderItemDto : orderDto.getItems()) {
            String productCode = orderItemDto.getProductId();
            Inventory inventoryFromDB = inventoryMap.get(productCode);

            int productCount = orderItemDto.getQuantity();
            if (productCount <= inventoryFromDB.getAvailableQuantity()) {
                inventoryFromDB
                        .setReservedItems(inventoryFromDB.getReservedItems() + productCount)
                        .setAvailableQuantity(
                                inventoryFromDB.getAvailableQuantity() - productCount);
                updatedInventoryList.add(inventoryFromDB);
            } else {
                log.info(
                        "Setting status as REJECT for OrderId in Inventory Service as quantity not available : {}",
                        orderDto.getOrderId());
                orderDto.setStatus("REJECT");
                break;
            }
        }

        if (updatedInventoryList.size() == inventoryListFromDB.size()) {
            orderDto.setStatus("ACCEPT");
            log.info(
                    "Setting status as ACCEPT for inventoryIds : {}",
                    updatedInventoryList.stream().map(Inventory::getId).toList());
            inventoryRepository.saveAll(updatedInventoryList);
        }

        orderDto.setSource("INVENTORY");
        // Send order to Kafka
        kafkaTemplate.send(AppConstants.STOCK_ORDERS_TOPIC, orderDto.getOrderId(), orderDto);
        log.info(
                "Sent Order after reserving : {} from inventory service to topic {}",
                orderDto,
                AppConstants.STOCK_ORDERS_TOPIC);
    }

    public void confirm(OrderDto orderDto) {
        log.info("Confirming Order in Inventory Service {}", orderDto);
        List<String> productCodeList =
                orderDto.getItems().stream().map(OrderItemDto::getProductId).toList();

        Map<String, Inventory> inventoryMap =
                inventoryJOOQRepository.findByProductCodeIn(productCodeList).stream()
                        .collect(Collectors.toMap(Inventory::getProductCode, Function.identity()));

        for (OrderItemDto orderItemDto : orderDto.getItems()) {
            String productId = orderItemDto.getProductId();
            Inventory inventory = inventoryMap.get(productId);

            if (inventory != null) {
                Integer productCount = orderItemDto.getQuantity();

                if ("CONFIRMED".equals(orderDto.getStatus())) {
                    inventory.setReservedItems(inventory.getReservedItems() - productCount);
                } else if (AppConstants.ROLLBACK.equals(orderDto.getStatus())
                        && !AppConstants.SOURCE.equalsIgnoreCase(orderDto.getSource())) {
                    inventory
                            .setReservedItems(inventory.getReservedItems() - productCount)
                            .setAvailableQuantity(inventory.getAvailableQuantity() + productCount);
                }
            }
        }

        inventoryRepository.saveAll(inventoryMap.values());

        log.info(
                "Saving inventoryIds : {} After Confirmation",
                inventoryMap.values().stream().map(Inventory::getId).toList());
    }
}
