/***
<p>
    Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice.services;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.inventoryservice.config.logging.Loggable;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.repositories.InventoryRepository;
import com.example.inventoryservice.utils.AppConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Loggable
public class InventoryOrderManageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryOrderManageService.class);

    private final InventoryRepository inventoryRepository;
    private final KafkaTemplate<Long, OrderDto> kafkaTemplate;

    public InventoryOrderManageService(
            InventoryRepository inventoryRepository, KafkaTemplate<Long, OrderDto> kafkaTemplate) {
        this.inventoryRepository = inventoryRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public void reserve(OrderDto orderDto) {
        LOGGER.info("Reserving Order in Inventory Service {}", orderDto);
        // Check if order status is not NEW
        if (!"NEW".equals(orderDto.getStatus())) {
            LOGGER.error(
                    "Order status is not NEW, Hence Ignoring OrderID :{}", orderDto.getOrderId());
            return;
        }
        List<String> productCodeList =
                orderDto.getItems().stream().map(OrderItemDto::getProductId).toList();

        // Using JPA repository instead of JOOQ
        List<Inventory> inventoryListFromDB =
                inventoryRepository.findByProductCodeIn(productCodeList);

        if (inventoryListFromDB.size() != productCodeList.size()) {
            LOGGER.error(
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
                LOGGER.info(
                        "Setting status as REJECT for OrderId in Inventory Service as quantity not available : {}",
                        orderDto.getOrderId());
                orderDto.setStatus("REJECT");
                break;
            }
        }

        if (updatedInventoryList.size() == inventoryListFromDB.size()) {
            orderDto.setStatus("ACCEPT");
            LOGGER.info(
                    "Setting status as ACCEPT for inventoryIds : {}",
                    updatedInventoryList.stream().map(Inventory::getId).toList());
            inventoryRepository.saveAll(updatedInventoryList);
        }

        orderDto.setSource("INVENTORY");
        // Send order to Kafka
        kafkaTemplate.send(AppConstants.STOCK_ORDERS_TOPIC, orderDto.getOrderId(), orderDto);
        LOGGER.info(
                "Sent Order after reserving : {} from inventory service to topic {}",
                orderDto,
                AppConstants.STOCK_ORDERS_TOPIC);
    }

    @Transactional
    public void confirm(OrderDto orderDto) {
        LOGGER.info("Confirming Order in Inventory Service {}", orderDto);

        List<String> productCodeList =
                orderDto.getItems().stream().map(OrderItemDto::getProductId).toList();

        Map<String, Inventory> inventoryMap =
                inventoryRepository.findByProductCodeIn(productCodeList).stream()
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

        LOGGER.info("Order confirmation completed for order ID: {}", orderDto.getOrderId());
    }
}
