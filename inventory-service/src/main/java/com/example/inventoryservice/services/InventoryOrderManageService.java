/***
<p>
    Licensed under MIT License Copyright (c) 2022-2025 Raja Kolli.
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
    public OrderDto reserve(OrderDto orderDto) {
        LOGGER.info("Reserving Order in Inventory Service {}", orderDto);
        // Check if order status is not NEW
        if (!"NEW".equals(orderDto.status())) {
            LOGGER.error("Order status is not NEW, Hence Ignoring OrderID :{}", orderDto.orderId());
            return orderDto;
        }
        List<String> productCodeList =
                orderDto.items().stream().map(OrderItemDto::productId).toList();

        // Using JPA repository instead of JOOQ
        List<Inventory> inventoryListFromDB =
                inventoryRepository.findByProductCodeIn(productCodeList);

        // Check if all requested products exist in the inventory
        if (inventoryListFromDB.size() != productCodeList.size()) {
            LOGGER.error(
                    "Not all products requested exist, Hence Ignoring OrderID : {}. Products in DB: {}, Products in Order: {}",
                    orderDto.orderId(),
                    inventoryListFromDB.stream().map(Inventory::getProductCode).toList(),
                    productCodeList);
            OrderDto rejectedOrderDto = orderDto.withStatusAndSource("REJECT", AppConstants.SOURCE);
            kafkaTemplate.send(
                    AppConstants.STOCK_ORDERS_TOPIC, rejectedOrderDto.orderId(), rejectedOrderDto);
            LOGGER.info(
                    "Sent Order with status REJECT (products not found): {} from inventory service to topic {}",
                    rejectedOrderDto,
                    AppConstants.STOCK_ORDERS_TOPIC);
            return rejectedOrderDto;
        }

        Map<String, Inventory> inventoryMap = new HashMap<>();
        inventoryListFromDB.forEach(
                inventory -> inventoryMap.put(inventory.getProductCode(), inventory));

        // Phase 1: Validation pass - no mutations
        boolean allItemsAvailable =
                orderDto.items().stream()
                        .allMatch(
                                itemDto -> {
                                    Inventory inventory = inventoryMap.get(itemDto.productId());
                                    return inventory != null
                                            && itemDto.quantity()
                                                    <= inventory.getAvailableQuantity();
                                });

        OrderDto finalOrderDto;
        List<Inventory> updatedInventoryList = new ArrayList<>();

        if (!allItemsAvailable) {
            LOGGER.info(
                    "Setting status as REJECT for OrderId in Inventory Service as quantity not available : {}",
                    orderDto.orderId());
            // As per review, sending REJECT status to Kafka if quantity not available
            finalOrderDto = orderDto.withStatus("REJECT");
            // No inventory changes are saved as updatedInventoryList is empty and saveAll won't be
            // called.
        } else {
            // Phase 2: Mutation pass - safe to persist
            for (OrderItemDto orderItemDto : orderDto.items()) {
                Inventory inventoryFromDB = inventoryMap.get(orderItemDto.productId());
                int productCount = orderItemDto.quantity();
                inventoryFromDB.setReservedItems(inventoryFromDB.getReservedItems() + productCount);
                inventoryFromDB.setAvailableQuantity(
                        inventoryFromDB.getAvailableQuantity() - productCount);
                updatedInventoryList.add(inventoryFromDB);
            }
            // Persist changes
            inventoryRepository.saveAll(updatedInventoryList);
            finalOrderDto = orderDto.withStatus("ACCEPT");
            LOGGER.info(
                    "Setting status as ACCEPT for OrderId : {}, inventoryIds updated : {}",
                    orderDto.orderId(),
                    updatedInventoryList.stream().map(Inventory::getId).toList());
        }

        // Send order to Kafka
        OrderDto orderWithSource = finalOrderDto.withSource(AppConstants.SOURCE);
        kafkaTemplate.send(
                AppConstants.STOCK_ORDERS_TOPIC, orderWithSource.orderId(), orderWithSource);
        LOGGER.info(
                "Sent Order with status {} : {} from inventory service to topic {}",
                orderWithSource.status(),
                orderWithSource,
                AppConstants.STOCK_ORDERS_TOPIC);
        return orderWithSource;
    }

    @Transactional
    public void confirm(OrderDto orderDto) {
        LOGGER.info("Confirming Order in Inventory Service {}", orderDto);

        List<String> productCodeList =
                orderDto.items().stream().map(OrderItemDto::productId).toList();

        Map<String, Inventory> inventoryMap =
                inventoryRepository.findByProductCodeIn(productCodeList).stream()
                        .collect(Collectors.toMap(Inventory::getProductCode, Function.identity()));

        for (OrderItemDto orderItemDto : orderDto.items()) {
            String productId = orderItemDto.productId();
            Inventory inventory = inventoryMap.get(productId);

            if (inventory != null) {
                Integer productCount = orderItemDto.quantity();

                if ("CONFIRMED".equals(orderDto.status())) {
                    inventory.setReservedItems(inventory.getReservedItems() - productCount);
                } else if (AppConstants.ROLLBACK.equals(orderDto.status())
                        && !AppConstants.SOURCE.equalsIgnoreCase(orderDto.source())) {
                    inventory
                            .setReservedItems(inventory.getReservedItems() - productCount)
                            .setAvailableQuantity(inventory.getAvailableQuantity() + productCount);
                }
            }
        }

        inventoryRepository.saveAll(inventoryMap.values());

        LOGGER.info("Order confirmation completed for order ID: {}", orderDto.orderId());
    }
}
