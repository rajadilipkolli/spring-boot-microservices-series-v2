/***
<p>
    Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.inventoryservice.inventory.InventoryOrderManageService;
import com.example.inventoryservice.inventory.domain.models.OrderDto;
import com.example.inventoryservice.inventory.events.InventoryProcessedEvent;
import com.example.inventoryservice.inventory.utils.AppConstants;
import com.example.inventoryservice.inventory.utils.logging.Loggable;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Loggable
class InventoryOrderManageServiceImpl implements InventoryOrderManageService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(InventoryOrderManageServiceImpl.class);

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Counter inventoryReservationsCounter;
    private final Counter inventoryFailuresCounter;

    /** Creates the service and obtains reservation and failure counters from the registry. */
    public InventoryOrderManageServiceImpl(
            InventoryRepository inventoryRepository,
            MeterRegistry meterRegistry,
            ApplicationEventPublisher eventPublisher) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.inventoryReservationsCounter = meterRegistry.counter("inventory_reservations");
        this.inventoryFailuresCounter = meterRegistry.counter("inventory_failures");
    }

    /**
     * Reserves available inventory for a new order and publishes the reservation outcome.
     *
     * <p>Orders whose status is not {@code NEW} are returned unchanged without publishing an event.
     * New orders are rejected without stock changes if products are missing, product codes are
     * repeated, or any requested quantity exceeds availability. Accepted orders move the requested
     * quantities from available to reserved stock. Both outcomes publish an {@link
     * InventoryProcessedEvent}; persistence and event publication failures propagate to the caller.
     *
     * @param orderDto order whose items should be reserved
     * @return the order with status {@code ACCEPT} or {@code REJECT} and source {@code INVENTORY},
     *     or the original order if its status is not {@code NEW}
     */
    @Transactional
    @Override
    public OrderDto reserve(OrderDto orderDto) {
        LOGGER.info("Reserving Order in Inventory Service {}", orderDto);
        // Check if order status is not NEW
        if (!"NEW".equals(orderDto.status())) {
            LOGGER.error("Order status is not NEW, Hence Ignoring OrderID :{}", orderDto.orderId());
            return orderDto;
        }
        List<String> productCodeList =
                orderDto.items().stream().map(OrderDto.OrderItemDto::productId).toList();

        // Using JPA repository for reading unpaged list to keep entities managed.
        // NOTE: Do not change this back to JOOQ for mutating operations.
        // JOOQ returns detached entities which can lead to StaleObjectStateException
        // when merged and saved if the version field mapping is not perfectly synchronized.
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
            this.inventoryFailuresCounter.increment();
            eventPublisher.publishEvent(new InventoryProcessedEvent(rejectedOrderDto));
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
            this.inventoryFailuresCounter.increment();
            // No inventory changes are saved as updatedInventoryList is empty and saveAll won't be
            // called.
        } else {
            // Phase 2: Mutation pass - safe to persist
            for (OrderDto.OrderItemDto orderItemDto : orderDto.items()) {
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
            this.inventoryReservationsCounter.increment();
            LOGGER.info(
                    "Setting status as ACCEPT for OrderId : {}, inventoryIds updated : {}",
                    orderDto.orderId(),
                    updatedInventoryList.stream().map(Inventory::getId).toList());
        }

        // Send order to Kafka
        OrderDto orderWithSource = finalOrderDto.withSource(AppConstants.SOURCE);
        eventPublisher.publishEvent(new InventoryProcessedEvent(orderWithSource));
        LOGGER.info(
                "Sent Order with status {} : {} from inventory service to topic {}",
                orderWithSource.status(),
                orderWithSource,
                AppConstants.STOCK_ORDERS_TOPIC);
        return orderWithSource;
    }

    /**
     * Decreases reserved stock for {@code CONFIRMED} orders, or restores it to available stock for
     * {@code ROLLBACK} orders whose source is not {@code INVENTORY} (case-insensitive). Missing
     * products are skipped and other statuses leave quantities unchanged. Changes are persisted
     * without publishing an event; persistence failures propagate to the caller.
     *
     * @param orderDto order whose item quantities are deducted from reserved stock
     */
    @Transactional
    @Override
    public void confirm(OrderDto orderDto) {
        LOGGER.info("Confirming Order in Inventory Service {}", orderDto);

        List<String> productCodeList =
                orderDto.items().stream().map(OrderDto.OrderItemDto::productId).toList();

        // Using JPA repository to keep entities managed and avoid StaleObjectStateException on
        // detached entities
        Map<String, Inventory> inventoryMap =
                inventoryRepository.findByProductCodeIn(productCodeList).stream()
                        .collect(Collectors.toMap(Inventory::getProductCode, Function.identity()));

        for (OrderDto.OrderItemDto orderItemDto : orderDto.items()) {
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
