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
import com.example.inventoryservice.inventory.jooq.tables.records.InventoryRecord;
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
import org.springframework.stereotype.Service;

@Service
@Loggable
class InventoryOrderManageServiceImpl implements InventoryOrderManageService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(InventoryOrderManageServiceImpl.class);

    private final InventoryRepository inventoryRepository;
    private final InventoryJOOQRepository inventoryJOOQRepository;
    private final InventoryMapper inventoryMapper;
    private final InventoryRetryHandler retryHandler;
    private final ProcessedOrderEventRepository processedOrderEventRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final List<OrderStockActionStrategy> stockActionStrategies;
    private final Counter inventoryReservationsCounter;
    private final Counter inventoryFailuresCounter;

    /** Creates the service and obtains reservation and failure counters from the registry. */
    public InventoryOrderManageServiceImpl(
            InventoryRepository inventoryRepository,
            InventoryJOOQRepository inventoryJOOQRepository,
            InventoryMapper inventoryMapper,
            InventoryRetryHandler retryHandler,
            ProcessedOrderEventRepository processedOrderEventRepository,
            MeterRegistry meterRegistry,
            ApplicationEventPublisher eventPublisher) {
        this(
                inventoryRepository,
                inventoryJOOQRepository,
                inventoryMapper,
                retryHandler,
                processedOrderEventRepository,
                meterRegistry,
                eventPublisher,
                List.of(
                        new ConfirmedOrderStockActionStrategy(),
                        new RollbackOrderStockActionStrategy()));
    }

    /**
     * Creates the service with the supplied stock actions. A null list selects the confirmation and
     * rollback defaults; an empty list selects no actions.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public InventoryOrderManageServiceImpl(
            InventoryRepository inventoryRepository,
            InventoryJOOQRepository inventoryJOOQRepository,
            InventoryMapper inventoryMapper,
            InventoryRetryHandler retryHandler,
            ProcessedOrderEventRepository processedOrderEventRepository,
            MeterRegistry meterRegistry,
            ApplicationEventPublisher eventPublisher,
            List<OrderStockActionStrategy> stockActionStrategies) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryJOOQRepository = inventoryJOOQRepository;
        this.inventoryMapper = inventoryMapper;
        this.retryHandler = retryHandler;
        this.processedOrderEventRepository = processedOrderEventRepository;
        this.eventPublisher = eventPublisher;
        this.stockActionStrategies =
                stockActionStrategies == null
                        ? List.of(
                                new ConfirmedOrderStockActionStrategy(),
                                new RollbackOrderStockActionStrategy())
                        : List.copyOf(stockActionStrategies);
        this.inventoryReservationsCounter = meterRegistry.counter("inventory_reservations");
        this.inventoryFailuresCounter = meterRegistry.counter("inventory_failures");
    }

    /**
     * Reserves available inventory for a new order and publishes the reservation outcome.
     *
     * <p>Non-new orders are returned unchanged. For a non-null order ID, a duplicate reservation
     * skips stock changes; a new nonblank attempt ID can replay the stored outcome. If the attempt
     * is blank, already claimed, or the outcome is unavailable, the order is returned unchanged
     * without an event. Null order IDs bypass duplicate tracking.
     *
     * <p>Missing or deleted products, repeated product codes, and insufficient quantities cause
     * rejection without stock changes. Otherwise, requested quantities move from available to
     * reserved stock. Accepted and rejected outcomes are published as {@link
     * InventoryProcessedEvent}s. Persistence and event publication failures propagate.
     *
     * @return the order with its reservation outcome and inventory source, or the original order
     *     when processing or replay is skipped
     */
    @Override
    public OrderDto reserve(OrderDto orderDto) {
        return retryHandler.executeWithRetry(() -> reserveAttempt(orderDto));
    }

    private OrderDto reserveAttempt(OrderDto orderDto) {
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

        // CQRS: Read active inventory records via jOOQ
        List<InventoryRecord> recordsFromDB =
                inventoryJOOQRepository.findByProductCodeInAndDeletedFalse(productCodeList);

        // Check if all requested products exist in active inventory
        if (recordsFromDB.size() != productCodeList.size()) {
            LOGGER.error(
                    "Not all products requested exist, Hence Ignoring OrderID : {}. Products in DB: {}, Products in Order: {}",
                    orderDto.orderId(),
                    recordsFromDB.stream().map(InventoryRecord::getProductCode).toList(),
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

        // Map jOOQ records to JPA entities for mutation
        Map<String, Inventory> inventoryMap = new HashMap<>();
        for (InventoryRecord record : recordsFromDB) {
            Inventory entity = inventoryMapper.toEntity(record);
            inventoryMap.put(entity.getProductCode(), entity);
        }

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
            // Persist changes via Spring Data JPA with custom fallback retry
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
     * Applies matching stock strategies to order items, including those for deleted inventory.
     * The default strategies decrease reserved stock for {@code CONFIRMED} orders and restore it to
     * available stock for {@code ROLLBACK} orders whose source is not {@code INVENTORY}
     * (case-insensitive). Missing products are skipped; supplied strategies can alter the behavior.
     *
     * <p>For non-null order IDs, duplicate confirmations with the same status are skipped. Null IDs
     * bypass duplicate tracking. No event is published; persistence failures propagate.
     */
    @Override
    public void confirm(OrderDto orderDto) {
        retryHandler.executeWithRetry(() -> {
                    confirmAttempt(orderDto);
                    return null;
                });
    }

    private void confirmAttempt(OrderDto orderDto) {
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

        // CQRS: Confirmation and rollback read via jOOQ (unfiltered so existing reservations can
        // complete or release)
        List<InventoryRecord> records =
                inventoryJOOQRepository.findByProductCodeIn(productCodeList);
        Map<String, Inventory> inventoryMap =
                records.stream()
                        .map(inventoryMapper::toEntity)
                        .collect(Collectors.toMap(Inventory::getProductCode, Function.identity()));

        for (OrderDto.OrderItemDto orderItemDto : orderDto.items()) {
            String productId = orderItemDto.productId();
            Inventory inventory = inventoryMap.get(productId);

            if (inventory != null) {
                for (OrderStockActionStrategy strategy : stockActionStrategies) {
                    if (strategy.supports(orderDto.status(), orderDto.source())) {
                        strategy.apply(inventory, orderItemDto);
                    }
                }
            }
        }

        // Persist changes via Spring Data JPA with custom fallback retry
        inventoryRepository.saveAll(inventoryMap.values());

        LOGGER.info("Order confirmation completed for order ID: {}", orderDto.orderId());
    }
}
