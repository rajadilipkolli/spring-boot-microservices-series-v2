/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.orderservice;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.OrderItemDto;
import com.example.orderservice.common.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

@TestMethodOrder(value = MethodOrderer.OrderAnnotation.class)
class OrderServiceApplicationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private KafkaTemplate<Long, OrderDto> kafkaTemplate;

    @Test
    @Order(1)
    void shouldFetchAllOrdersFromStream() {
        // waiting till is kafka stream is changed from PARTITIONS_ASSIGNED to RUNNING
        await().pollDelay(5, SECONDS)
                .atMost(10, SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () ->
                                this.mockMvc
                                        .perform(get("/api/orders/all"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.size()", is(0))));
    }

    @Test
    @Order(2)
    @Disabled("until infra for streams is set up")
    void shouldFetchAllOrdersFromStreamWhenDataIsPresent() {

        // Sending event to OrderTopic for joining
        OrderDto orderDto = new OrderDto();
        orderDto.setOrderId(151L);
        orderDto.setCustomerId(1001L);
        orderDto.setStatus("ACCEPT");
        orderDto.setSource("PAYMENT");
        OrderItemDto orderItemDto = new OrderItemDto();
        orderItemDto.setItemId(1L);
        orderItemDto.setProductId("P1");
        orderItemDto.setProductPrice(BigDecimal.TEN);
        orderItemDto.setQuantity(1);
        orderDto.setItems(List.of(orderItemDto));

        this.kafkaTemplate.send("orders", orderDto.getOrderId(), orderDto);

        // Sending events to both payment-orders, stock-orders for streaming to process and confirm
        OrderDto paymentOrderDto = new OrderDto();
        paymentOrderDto.setOrderId(151L);
        paymentOrderDto.setCustomerId(1001L);
        paymentOrderDto.setStatus("ACCEPT");
        paymentOrderDto.setSource("PAYMENT");
        OrderItemDto paymentOrderItemDto = new OrderItemDto();
        paymentOrderItemDto.setItemId(1L);
        paymentOrderItemDto.setProductId("P1");
        paymentOrderItemDto.setProductPrice(BigDecimal.TEN);
        paymentOrderItemDto.setQuantity(1);
        paymentOrderDto.setItems(List.of(paymentOrderItemDto));

        this.kafkaTemplate.send("payment-orders", paymentOrderDto.getOrderId(), paymentOrderDto);

        OrderDto stockOrderDto = new OrderDto();
        stockOrderDto.setOrderId(151L);
        stockOrderDto.setCustomerId(1001L);
        stockOrderDto.setStatus("ACCEPT");
        stockOrderDto.setSource("STOCK");
        OrderItemDto stockOrderItemDto = new OrderItemDto();
        stockOrderItemDto.setItemId(1L);
        stockOrderItemDto.setProductId("P1");
        stockOrderItemDto.setProductPrice(BigDecimal.TEN);
        stockOrderItemDto.setQuantity(1);
        stockOrderDto.setItems(List.of(stockOrderItemDto));

        this.kafkaTemplate.send("stock-orders", stockOrderDto.getOrderId(), stockOrderDto);

        await().atMost(30, SECONDS)
                .pollDelay(10, SECONDS)
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                this.mockMvc
                                        .perform(get("/api/orders/all"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.size()", is(1))));
    }
}
