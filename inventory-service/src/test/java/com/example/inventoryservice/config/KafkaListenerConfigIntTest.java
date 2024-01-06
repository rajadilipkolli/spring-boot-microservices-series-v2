/***
<p>
    Licensed under MIT License Copyright (c) 2024 Raja Kolli.
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
    void onNewOrderEvent() {
        inventoryRepository.deleteByProductCode("JUNIT_000");
        // Create inventory ensuring quantity is available
        Inventory inventory =
                inventoryRepository.save(
                        new Inventory().setProductCode("JUNIT_000").setAvailableQuantity(1000));

        assertThat(stockOrderListener.getCountDownLatch().getCount()).isEqualTo(1);
        // publish event
        OrderDto orderDto = MockTestData.getOrderDto("ORDER");
        kafkaTemplate.send("orders", orderDto.getOrderId(), orderDto);

        await().untilAsserted(
                        () -> {
                            Optional<Inventory> optionalInventory =
                                    inventoryRepository.findById(inventory.getId());
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
}
