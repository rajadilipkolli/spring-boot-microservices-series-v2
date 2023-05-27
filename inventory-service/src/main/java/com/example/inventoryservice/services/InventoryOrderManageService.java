/* Licensed under Apache-2.0 2022-2023 */
package com.example.inventoryservice.services;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.repositories.InventoryRepository;
import com.example.inventoryservice.utils.AppConstants;
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
        List<Inventory> inventoryList =
                inventoryRepository.findByProductCodeInAndQuantityAvailable(productCodeList);
        if (inventoryList.size() != productCodeList.size()) {
            log.error(
                    "Not all products requested exists, Hence Rejecting OrderID :{}",
                    orderDto.getOrderId());
            orderDto.setStatus("REJECT");
        } else {
            log.info("All Products Exists");
            Map<String, Inventory> inventoryMap =
                    inventoryList.stream()
                            .collect(
                                    Collectors.toMap(
                                            Inventory::getProductCode, Function.identity()));

            List<Inventory> persistInventoryList =
                    orderDto.getItems().stream()
                            .map(
                                    orderItemDto -> {
                                        Inventory inventory =
                                                inventoryMap.get(orderItemDto.getProductId());
                                        int productCount = orderItemDto.getQuantity();
                                        inventory.setReservedItems(
                                                inventory.getReservedItems() + productCount);
                                        inventory.setAvailableQuantity(
                                                inventory.getAvailableQuantity() - productCount);
                                        return inventory;
                                    })
                            .collect(Collectors.toList());

            // Update order status
            orderDto.setStatus("ACCEPT");
            log.info(
                    "Setting status as ACCEPT for inventoryIds : {}",
                    persistInventoryList.stream().map(Inventory::getId).toList());
            inventoryRepository.saveAll(persistInventoryList);
        }
        // Send order to Kafka
        kafkaTemplate.send(AppConstants.STOCK_ORDERS_TOPIC, orderDto.getOrderId(), orderDto);
        log.info(
                "Sent Order after reserving : {} from inventory service to topic {}",
                orderDto,
                AppConstants.STOCK_ORDERS_TOPIC);
    }

    public void confirm(OrderDto orderDto) {
        List<String> productCodeList =
                orderDto.getItems().stream().map(OrderItemDto::getProductId).toList();
        List<Inventory> inventoryList = inventoryRepository.findByProductCodeIn(productCodeList);

        Map<String, Inventory> inventoryMap =
                inventoryList.stream()
                        .collect(Collectors.toMap(Inventory::getProductCode, Function.identity()));

        List<Inventory> updatedInventoryList =
                orderDto.getItems().stream()
                        .filter(
                                orderItemDto ->
                                        inventoryMap.containsKey(orderItemDto.getProductId()))
                        .map(
                                orderItemDto -> {
                                    Inventory inventory =
                                            inventoryMap.get(orderItemDto.getProductId());
                                    Integer productCount = orderItemDto.getQuantity();
                                    if ("CONFIRMED".equals(orderDto.getStatus())) {
                                        inventory.setReservedItems(
                                                inventory.getReservedItems() - productCount);
                                    } else if (AppConstants.ROLLBACK.equals(orderDto.getStatus())
                                            && !(AppConstants.SOURCE.equalsIgnoreCase(
                                                    orderDto.getSource()))) {
                                        inventory.setReservedItems(
                                                inventory.getReservedItems() - productCount);
                                        inventory.setAvailableQuantity(
                                                inventory.getAvailableQuantity() + productCount);
                                    }
                                    return inventory;
                                })
                        .collect(Collectors.toList());

        inventoryRepository.saveAll(updatedInventoryList);
        log.info(
                "Saving inventoryIds : {} After Confirmation",
                updatedInventoryList.stream().map(Inventory::getId).toList());
    }
}
