/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.orderservice;

import static com.example.orderservice.util.TestData.getOrderDto;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.common.AbstractIntegrationTest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@Disabled
@TestMethodOrder(value = MethodOrderer.OrderAnnotation.class)
class OrderServiceApplicationIntegrationTest extends AbstractIntegrationTest {

    @Test
    @Order(1)
    void shouldFetchAllOrdersFromStream() {
        // waiting till is kafka stream is changed from PARTITIONS_ASSIGNED to RUNNING
        await().pollDelay(5, SECONDS)
                .atMost(15, SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () ->
                                this.mockMvc
                                        .perform(get("/api/orders/all?pageNo=1000"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.size()", is(0))));
    }

    @Test
    @Order(2)
    void shouldFetchAllOrdersFromStreamWhenDataIsPresent() {

        // Sending event to OrderTopic for joining
        OrderDto orderDto = getOrderDto("ORDER", 10000L);

        this.kafkaTemplate.send("orders", orderDto.orderId(), orderDto);

        // Sending events to both payment-orders, stock-orders for streaming to process
        // and confirm
        OrderDto paymentOrderDto = getOrderDto("PAYMENT", 10000L);

        this.kafkaTemplate.send("payment-orders", paymentOrderDto.orderId(), paymentOrderDto);

        OrderDto stockOrderDto = getOrderDto("STOCK", 10000L);

        this.kafkaTemplate.send("stock-orders", stockOrderDto.orderId(), stockOrderDto);

        await().atMost(1, TimeUnit.MINUTES)
                .pollDelay(10, SECONDS)
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                this.mockMvc
                                        .perform(get("/api/orders/all?pageNo=100"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.size()", is(0))));
    }
}
