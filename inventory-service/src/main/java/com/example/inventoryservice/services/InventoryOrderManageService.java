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
import com.example.inventoryservice.repositories.InventoryJOOQRepository;
import com.example.inventoryservice.repositories.InventoryRepository;
import com.example.inventoryservice.utils.AppConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Loggable
public class InventoryOrderManageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InventoryOrderManageService.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryJOOQRepository inventoryJOOQRepository;
    private final KafkaTemplate<Long, OrderDto> kafkaTemplate;

    public InventoryOrderManageService(
            InventoryRepository inventoryRepository,
            InventoryJOOQRepository inventoryJOOQRepository,
            KafkaTemplate<Long, OrderDto> kafkaTemplate) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryJOOQRepository = inventoryJOOQRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

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

        List<Inventory> inventoryListFromDB =
                inventoryJOOQRepository.findByProductCodeIn(productCodeList);

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

    public void confirm(OrderDto orderDto) {
        LOGGER.info("Confirming Order in Inventory Service {}", orderDto);
        List<String> productCodeList =
                orderDto.getItems().stream().map(OrderItemDto::getProductId).toList();

        // First, use the JOOQ repository to fetch all items
        List<Inventory> inventoryListFromDB =
                inventoryJOOQRepository.findByProductCodeIn(productCodeList);

        // Group items by product ID for easier processing
        Map<String, Integer> productQuantityMap = new HashMap<>();
        for (OrderItemDto item : orderDto.getItems()) {
            productQuantityMap.put(item.getProductId(), item.getQuantity());
        }

        List<Inventory> updatedInventories = new ArrayList<>();

        // Process each inventory item individually to avoid optimistic locking issues
        for (Inventory inventory : inventoryListFromDB) {
            String productCode = inventory.getProductCode();
            Integer quantity = productQuantityMap.get(productCode);

            // Apply the appropriate update logic based on status and source
            if ("CONFIRMED".equals(orderDto.getStatus())) {
                inventory.setReservedItems(inventory.getReservedItems() - quantity);
                updatedInventories.add(inventory);
            } else if (AppConstants.ROLLBACK.equals(orderDto.getStatus())
                    && !AppConstants.SOURCE.equalsIgnoreCase(orderDto.getSource())) {
                inventory
                        .setReservedItems(inventory.getReservedItems() - quantity)
                        .setAvailableQuantity(inventory.getAvailableQuantity() + quantity);
                updatedInventories.add(inventory);
            }
        }

        // Save all updated inventories if there are any changes
        if (!updatedInventories.isEmpty()) {
            try {
                inventoryRepository.saveAll(updatedInventories);
            } catch (Exception e) {
                LOGGER.error("Failed to update inventory items: {}", e.getMessage());
                // For real production code, we might want to implement individual retry logic here
                throw e;
            }
        }

        LOGGER.info("Order confirmation completed for order ID: {}", orderDto.getOrderId());
    }
}
