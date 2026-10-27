/***
<p>
    Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import com.example.common.dto.OrderDto;
import com.example.common.logging.Loggable;
import com.example.inventoryservice.inventory.InventoryOrderManageService;
import com.example.inventoryservice.inventory.events.InventoryProcessedEvent;
import com.example.inventoryservice.inventory.utils.AppConstants;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Loggable
class InventoryOrderManageServiceImpl implements InventoryOrderManageService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(InventoryOrderManageServiceImpl.class);

    private final InventoryRepository inventoryRepository;
    private final ProcessedOrderEventRepository processedOrderEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Counter inventoryReservationsCounter;
    private final Counter inventoryFailuresCounter;

    /** Creates the service and obtains reservation and failure counters from the registry. */
    public InventoryOrderManageServiceImpl(
            InventoryRepository inventoryRepository,
            ProcessedOrderEventRepository processedOrderEventRepository,
            MeterRegistry meterRegistry,
            ApplicationEventPublisher eventPublisher) {
        this.inventoryRepository = inventoryRepository;
        this.processedOrderEventRepository = processedOrderEventRepository;
        this.eventPublisher = eventPublisher;
        this.inventoryReservationsCounter = meterRegistry.counter("inventory_reservations");
        this.inventoryFailuresCounter = meterRegistry.counter("inventory_failures");
    }

    /**
     * Reserves available inventory for a new order and publishes the reservation outcome.
     *
     * <p>Orders whose status is not {@code NEW} are returned unchanged without publishing an event.
     * For non-null IDs, an existing {@code RESERVE} record skips stock changes. A duplicate with a
     * nonblank attempt ID not already claimed for a reply publishes the stored outcome using the
     * supplied order's items and attempt ID. Missing outcomes, null or blank attempt IDs, and
     * existing reply claims return the order unchanged without an event. A new reply attempt is
     * recorded even if the outcome is unavailable. Accepted and rejected reservations store their
     * outcome for non-null IDs; null IDs bypass duplicate tracking.
     *
     * <p>New orders are rejected without stock changes if products are missing or deleted, product
     * codes are repeated, or any requested quantity exceeds availability. Accepted orders move the
     * requested quantities from available to reserved stock. Both outcomes publish an {@link
     * InventoryProcessedEvent}. Optimistic locking failures are eligible for up to three retries
     * with an initial delay of one second, doubling between retries. Unresolved persistence and
     * event publication failures propagate to the caller.
     *
     * @param orderDto order whose items should be reserved
     * @return the order with the new or stored reservation status and source {@code INVENTORY}, or
     *     the original order if its status is not {@code NEW} or a duplicate reply is skipped
     */
    @Transactional
    @Retryable(
            includes = OptimisticLockingFailureException.class,
            maxRetries = 3,
            delay = 1000,
            multiplier = 2.0)
    @Override
    public OrderDto reserve(OrderDto orderDto) {
        LOGGER.info("Reserving Order in Inventory Service {}", orderDto);
        // Check if order status is not NEW
        if (!"NEW".equals(orderDto.status())) {
            LOGGER.error("Order status is not NEW, Hence Ignoring OrderID :{}", orderDto.orderId());
            return orderDto;
        }

        if (orderDto.orderId() != null) {
            int claimed =
                    processedOrderEventRepository.claim(
                            orderDto.orderId(), "RESERVE", Instant.now());
            if (claimed == 0) {
                // Duplicate reserve event path
                if (orderDto.attemptId() == null || orderDto.attemptId().isBlank()) {
                    LOGGER.warn(
                            "Order {} reserve event has already been processed with blank attempt. Skipping duplicate.",
                            orderDto.orderId());
                    return orderDto;
                }

                int replyClaimed =
                        processedOrderEventRepository.claim(
                                orderDto.orderId(),
                                "RESERVE_REPLY:" + orderDto.attemptId(),
                                Instant.now());
                if (replyClaimed == 0) {
                    LOGGER.warn(
                            "Order {} reserve reply for attempt {} has already been processed. Skipping duplicate.",
                            orderDto.orderId(),
                            orderDto.attemptId());
                    return orderDto;
                }

                var storedOutcomeOpt =
                        processedOrderEventRepository.findResultStatusByOrderIdAndReserveEventType(
                                orderDto.orderId());
                if (storedOutcomeOpt.isEmpty() || storedOutcomeOpt.get() == null) {
                    LOGGER.warn(
                            "Order {} reserve outcome is not available. Skipping replay.",
                            orderDto.orderId());
                    return orderDto;
                }

                String outcomeStatus = storedOutcomeOpt.get();
                OrderDto replayOrderDto =
                        orderDto.withStatusAndSource(outcomeStatus, AppConstants.SOURCE);
                eventPublisher.publishEvent(new InventoryProcessedEvent(replayOrderDto));
                LOGGER.info(
                        "Replayed Order with status {} for attempt {} : {} from inventory service to topic {}",
                        replayOrderDto.status(),
                        orderDto.attemptId(),
                        replayOrderDto,
                        AppConstants.STOCK_ORDERS_TOPIC);
                return replayOrderDto;
            }
        }

        List<String> productCodeList =
                orderDto.items().stream().map(OrderDto.OrderItemDto::productId).toList();

        // Using JPA repository for reading unpaged list to keep entities managed.
        // NOTE: Do not change this back to JOOQ for mutating operations.
        // JOOQ returns detached entities which can lead to StaleObjectStateException
        // when merged and saved if the version field mapping is not perfectly synchronized.
        List<Inventory> inventoryListFromDB =
                inventoryRepository.findByProductCodeInAndDeletedFalse(productCodeList);

        // Check if all requested products exist in the inventory
        if (inventoryListFromDB.size() != productCodeList.size()) {
            LOGGER.error(
                    "Not all products requested exist, Hence Ignoring OrderID : {}. Products in DB: {}, Products in Order: {}",
                    orderDto.orderId(),
                    inventoryListFromDB.stream().map(Inventory::getProductCode).toList(),
                    productCodeList);
            OrderDto rejectedOrderDto = orderDto.withStatusAndSource("REJECT", AppConstants.SOURCE);
            this.inventoryFailuresCounter.increment();
            if (orderDto.orderId() != null) {
                processedOrderEventRepository.updateResultStatus(
                        orderDto.orderId(), "RESERVE", "REJECT");
                if (orderDto.attemptId() != null && !orderDto.attemptId().isBlank()) {
                    processedOrderEventRepository.claim(
                            orderDto.orderId(),
                            "RESERVE_REPLY:" + orderDto.attemptId(),
                            Instant.now());
                }
            }
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
            if (orderDto.orderId() != null) {
                processedOrderEventRepository.updateResultStatus(
                        orderDto.orderId(), "RESERVE", "REJECT");
            }
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
            if (orderDto.orderId() != null) {
                processedOrderEventRepository.updateResultStatus(
                        orderDto.orderId(), "RESERVE", "ACCEPT");
            }
            LOGGER.info(
                    "Setting status as ACCEPT for OrderId : {}, inventoryIds updated : {}",
                    orderDto.orderId(),
                    updatedInventoryList.stream().map(Inventory::getId).toList());
        }

        // Send order to Kafka
        OrderDto orderWithSource = finalOrderDto.withSource(AppConstants.SOURCE);
        if (orderDto.orderId() != null
                && orderDto.attemptId() != null
                && !orderDto.attemptId().isBlank()) {
            processedOrderEventRepository.claim(
                    orderDto.orderId(), "RESERVE_REPLY:" + orderDto.attemptId(), Instant.now());
        }
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
     * <p>For non-null order IDs, an existing {@code CONFIRM_<status>} record skips processing;
     * otherwise that record is saved even when no quantities change. Null IDs bypass duplicate
     * tracking. Optimistic locking failures are eligible for up to three retries with an initial
     * delay of one second, doubling between retries; failures remaining afterward propagate.
     *
     * @param orderDto order whose item quantities are deducted from reserved stock
     */
    @Transactional
    @Retryable(
            includes = OptimisticLockingFailureException.class,
            maxRetries = 3,
            multiplier = 2.0,
            delay = 1000)
    @Override
    public void confirm(OrderDto orderDto) {
        LOGGER.info("Confirming Order in Inventory Service {}", orderDto);

        String eventType = "CONFIRM_" + orderDto.status();
        if (orderDto.orderId() != null) {
            int claimed =
                    processedOrderEventRepository.claim(
                            orderDto.orderId(), eventType, Instant.now());
            if (claimed == 0) {
                LOGGER.warn(
                        "Order {} confirm ({}) event has already been processed. Skipping duplicate.",
                        orderDto.orderId(),
                        orderDto.status());
                return;
            }
        }

        List<String> productCodeList =
                orderDto.items().stream().map(OrderDto.OrderItemDto::productId).toList();

        // Confirmation and rollback remain unfiltered so existing reservations can complete or
        // release
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
