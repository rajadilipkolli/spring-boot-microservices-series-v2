/***
<p>
    Licensed under MIT License Copyright (c) 2024-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.common.dto.OrderDto;
import com.example.common.dto.ProductDto;
import com.example.inventoryservice.inventory.jooq.tables.records.InventoryRecord;
import com.example.inventoryservice.inventory.utils.AppConstants;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KafkaListenerConfigIntTest extends AbstractIntegrationTest {

    @BeforeEach
    void setUp() {
        processedOrderEventRepository.deleteAllInBatch();
    }

    @Test
    void onNewOrderEvent() {
        inventoryRepository.deleteByProductCode("JUNIT_000");
        // Create inventory ensuring quantity is available
        Inventory inventory =
                inventoryRepository.save(
                        new Inventory().setProductCode("JUNIT_000").setAvailableQuantity(1000));

        stockOrderListener.reset();
        assertThat(stockOrderListener.getCountDownLatch().getCount()).isEqualTo(1);
        // publish event
        OrderDto orderDto = MockTestData.getOrderDto("ORDER");
        kafkaTemplate.send(
                "orders",
                String.valueOf(orderDto.orderId()),
                jsonMapper.writeValueAsBytes(orderDto));

        await().untilAsserted(
                        () -> {
                            Optional<InventoryRecord> optionalInventory =
                                    inventoryJOOQRepository.findById(inventory.getId());
                            assertThat(optionalInventory).isPresent();
                            InventoryRecord inventoryFromDB = optionalInventory.get();
                            assertThat(inventoryFromDB)
                                    .satisfies(
                                            inventory1 -> {
                                                assertThat(inventory1.getQuantity()).isEqualTo(990);
                                                assertThat(inventory1.getReservedItems())
                                                        .isEqualTo(10);
                                            });
                            assertThat(stockOrderListener.getCountDownLatch().getCount()).isZero();
                        });
    }

    /** Verifies a catalog product event with a decimal price creates an inventory entry. */
    @Test
    void onSaveProductEvent() {
        inventoryRepository.deleteByProductCode("P001");

        // Simulating the catalog-service product shape which is a flat JSON without __TypeId__
        ProductDto productDto =
                new ProductDto("P001", "Product 1", "Description 1", BigDecimal.TEN);
        kafkaTemplate.send(
                AppConstants.PRODUCT_TOPIC, "1001", jsonMapper.writeValueAsBytes(productDto));

        await().untilAsserted(
                        () ->
                                assertThat(
                                                inventoryJOOQRepository
                                                        .findByProductCodeAndDeletedFalse("P001"))
                                        .isPresent());
    }

    @Test
    void onDeleteProductEvent() {
        inventoryRepository.deleteByProductCode("P_DEL_001");
        // Create active inventory
        inventoryRepository.save(
                new Inventory().setProductCode("P_DEL_001").setAvailableQuantity(100));

        // Event with deleted: true
        ProductDto deleteDto =
                new ProductDto("P_DEL_001", "Product Del", "Desc", BigDecimal.TEN, true);
        kafkaTemplate.send(
                AppConstants.PRODUCT_TOPIC, "P_DEL_001", jsonMapper.writeValueAsBytes(deleteDto));

        await().untilAsserted(
                        () -> {
                            // Unfiltered lookup shows it is retained with deleted = true
                            Optional<Inventory> opt =
                                    inventoryRepository.findByProductCode("P_DEL_001");
                            assertThat(opt).isPresent();
                            assertThat(opt.get().isDeleted()).isTrue();

                            // Filtered lookup returns empty
                            assertThat(
                                            inventoryJOOQRepository
                                                    .findByProductCodeAndDeletedFalse("P_DEL_001"))
                                    .isEmpty();
                        });
    }

    @Test
    void onProductEventWithUnknownFields() {
        inventoryRepository.deleteByProductCode("P_UNKNOWN_001");

        // JSON payload containing unknown fields
        String rawJson =
                "{\"code\":\"P_UNKNOWN_001\",\"productName\":\"Unknown Field Test\",\"price\":15.00,\"unknownProperty\":\"ignored\"}";
        kafkaTemplate.send(
                AppConstants.PRODUCT_TOPIC,
                "P_UNKNOWN_001",
                rawJson.getBytes(StandardCharsets.UTF_8));

        await().untilAsserted(
                        () ->
                                assertThat(
                                                inventoryJOOQRepository
                                                        .findByProductCodeAndDeletedFalse(
                                                                "P_UNKNOWN_001"))
                                        .isPresent());
    }

    @Test
    void onDuplicateNewOrderEvent_DecreasesAvailableQuantityOnlyOnce() {
        String code = "JUNIT_DUP_ORDER";
        inventoryRepository.deleteByProductCode(code);
        Inventory inventory =
                inventoryRepository.save(
                        new Inventory().setProductCode(code).setAvailableQuantity(100));

        OrderDto.OrderItemDto item = new OrderDto.OrderItemDto(1L, code, 10, BigDecimal.TEN);
        OrderDto orderDto = new OrderDto(8888L, 2L, "NEW", "ORDER", java.util.List.of(item));
        byte[] payload = jsonMapper.writeValueAsBytes(orderDto);

        // Send duplicate NEW events with key = orderId
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), payload);
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), payload);

        // A sentinel message to ensure both messages were processed by the single-partition order
        // listener
        OrderDto sentinelDto = new OrderDto(8889L, 2L, "NEW", "ORDER", java.util.List.of(item));
        kafkaTemplate.send(
                "orders",
                String.valueOf(orderDto.orderId()),
                jsonMapper.writeValueAsBytes(sentinelDto));

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(
                        () -> {
                            Optional<InventoryRecord> opt =
                                    inventoryJOOQRepository.findById(inventory.getId());
                            assertThat(opt).isPresent();
                            // First order 8888 took 10 (reserved 10, available 90).
                            // Sentinel 8889 took 10 (reserved 20, available 80).
                            // If 8888 duplicate was processed, available would be 70 and reserved
                            // 30.
                            InventoryRecord record = opt.get();
                            assertThat(record.getReservedItems()).isEqualTo(20);
                            assertThat(record.getQuantity()).isEqualTo(80);
                        });
    }

    @Test
    void onDuplicateProductEvent_CreatesOnlyOneProductRow() {
        String code = "P_DUP_001";
        inventoryRepository.deleteByProductCode(code);

        ProductDto productDto = new ProductDto(code, "Dup Product", "Desc", BigDecimal.TEN);
        byte[] payload = jsonMapper.writeValueAsBytes(productDto);

        // Send duplicate product events
        kafkaTemplate.send(AppConstants.PRODUCT_TOPIC, code, payload);
        kafkaTemplate.send(AppConstants.PRODUCT_TOPIC, code, payload);

        // Sentinel product to synchronize
        String sentinelCode = "P_DUP_SENTINEL";
        inventoryRepository.deleteByProductCode(sentinelCode);
        ProductDto sentinelDto = new ProductDto(sentinelCode, "Sentinel", "Desc", BigDecimal.TEN);
        kafkaTemplate.send(
                AppConstants.PRODUCT_TOPIC, code, jsonMapper.writeValueAsBytes(sentinelDto));

        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(
                        () -> {
                            assertThat(
                                            inventoryJOOQRepository
                                                    .findByProductCodeAndDeletedFalse(sentinelCode))
                                    .isPresent();
                            // Verify exactly one row for P_DUP_001
                            assertThat(inventoryRepository.findByProductCode(code)).isPresent();
                        });
    }
}
