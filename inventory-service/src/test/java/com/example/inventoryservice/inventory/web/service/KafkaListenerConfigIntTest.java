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
import com.example.inventoryservice.inventory.utils.AppConstants;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KafkaListenerConfigIntTest extends AbstractIntegrationTest {

    @Test
    void onNewOrderEvent() {
        inventoryJOOQRepository.deleteByProductCode("JUNIT_000");
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
                            Optional<Inventory> optionalInventory =
                                    inventoryJOOQRepository.findById(inventory.getId());
                            assertThat(optionalInventory).isPresent();
                            Inventory inventoryFromDB = optionalInventory.get();
                            assertThat(inventoryFromDB)
                                    .satisfies(
                                            inventory1 -> {
                                                assertThat(inventory1.getAvailableQuantity())
                                                        .isEqualTo(990);
                                                assertThat(inventory1.getReservedItems())
                                                        .isEqualTo(10);
                                            });
                            assertThat(stockOrderListener.getCountDownLatch().getCount()).isZero();
                        });
    }

    /** Verifies a catalog product event with a decimal price creates an inventory entry. */
    @Test
    void onSaveProductEvent() {
        inventoryJOOQRepository.deleteByProductCode("P001");

        // Simulating the catalog-service product shape which is a flat JSON without __TypeId__
        ProductDto productDto =
                new ProductDto("P001", "Product 1", "Description 1", BigDecimal.TEN);
        kafkaTemplate.send(
                AppConstants.PRODUCT_TOPIC, "1001", jsonMapper.writeValueAsBytes(productDto));

        await().untilAsserted(
                        () ->
                                assertThat(inventoryJOOQRepository.findByProductCode("P001"))
                                        .isPresent());
    }

    @Test
    void onDeleteProductEvent() {
        inventoryJOOQRepository.deleteByProductCode("P_DEL_001");
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
                            assertThat(inventoryJOOQRepository.findByProductCode("P_DEL_001"))
                                    .isEmpty();
                        });
    }

    @Test
    void onProductEventWithUnknownFields() {
        inventoryJOOQRepository.deleteByProductCode("P_UNKNOWN_001");

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
                                                inventoryJOOQRepository.findByProductCode(
                                                        "P_UNKNOWN_001"))
                                        .isPresent());
    }
}
