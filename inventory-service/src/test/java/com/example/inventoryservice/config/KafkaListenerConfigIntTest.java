/***
<p>
    Licensed under MIT License Copyright (c) 2024-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.inventoryservice.common.AbstractIntegrationTest;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.model.payload.OrderDto;
import com.example.inventoryservice.util.MockTestData;
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

        assertThat(stockOrderListener.getCountDownLatch().getCount()).isEqualTo(1);
        // publish event
        OrderDto orderDto = MockTestData.getOrderDto("ORDER");
        kafkaTemplate.send("orders", String.valueOf(orderDto.orderId()), orderDto);

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

    @Test
    void onSaveProductEvent() {
        inventoryJOOQRepository.deleteByProductCode("P001");

        // Simulating the catalog-service product shape which is a flat JSON without __TypeId__
        com.example.inventoryservice.model.payload.ProductDto productDto =
                new com.example.inventoryservice.model.payload.ProductDto(
                        "P001", "Product 1", "Description 1", 10.0);
        kafkaTemplate.send(
                com.example.inventoryservice.utils.AppConstants.PRODUCT_TOPIC, "1001", productDto);

        await().untilAsserted(
                        () -> {
                            assertThat(inventoryJOOQRepository.findByProductCode("P001"))
                                    .isPresent();
                        });
    }
}
