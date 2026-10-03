/***
<p>
    Licensed under MIT License Copyright (c) 2024-2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.common.dtos.OrderDto;
import com.example.inventoryservice.common.AbstractIntegrationTest;
import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.util.MockTestData;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class KafkaListenerConfigIntTest extends AbstractIntegrationTest {

    @Test
    void onNewOrderEventIsIdempotent() {
        inventoryJOOQRepository.deleteByProductCode("JUNIT_000");

        Inventory inventory =
                inventoryRepository.save(
                        new Inventory().setProductCode("JUNIT_000").setAvailableQuantity(1000));

        assertThat(stockOrderListener.getCountDownLatch().getCount()).isEqualTo(1);

        OrderDto orderDto = MockTestData.getOrderDto("ORDER");

        // Publish the exact same Kafka message twice.
        kafkaTemplate.send("orders", orderDto.orderId(), orderDto);
        kafkaTemplate.send("orders", orderDto.orderId(), orderDto);

        await().untilAsserted(
                        () -> {
                            Optional<Inventory> optionalInventory =
                                    inventoryJOOQRepository.findById(inventory.getId());

                            assertThat(optionalInventory).isPresent();

                            Inventory inventoryFromDB = optionalInventory.get();

                            // The duplicate message must not reserve the inventory twice.
                            assertThat(inventoryFromDB.getAvailableQuantity()).isEqualTo(990);
                            assertThat(inventoryFromDB.getReservedItems()).isEqualTo(10);

                            assertThat(stockOrderListener.getCountDownLatch().getCount()).isZero();
                        });
    }
}
