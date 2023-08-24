/* Licensed under Apache-2.0 2021-2023 */
package com.example.orderservice;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.common.AbstractIntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
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
        await().atMost(10, SECONDS)
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
    @Disabled
    void shouldFetchAllOrdersFromStreamWhenDataIsPresent() {

        // Sending events to both payment-orders and stock-orders
        OrderDto paymentOrderDto = new OrderDto();
        paymentOrderDto.setOrderId(151L);
        paymentOrderDto.setCustomerId(1001L);
        paymentOrderDto.setStatus("ACCEPT");
        paymentOrderDto.setSource("PAYMENT");
        paymentOrderDto.setItems(new ArrayList<>());

        this.kafkaTemplate.send("payment-orders", paymentOrderDto);
        OrderDto stockOrderDto = new OrderDto();
        stockOrderDto.setOrderId(151L);
        stockOrderDto.setCustomerId(1001L);
        stockOrderDto.setStatus("ACCEPT");
        stockOrderDto.setSource("STOCK");
        stockOrderDto.setItems(new ArrayList<>());

        this.kafkaTemplate.send("stock-orders", stockOrderDto);

        await().atMost(60, SECONDS)
                .pollInterval(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                this.mockMvc
                                        .perform(get("/api/orders/all"))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.size()", is(1))));
    }
}
