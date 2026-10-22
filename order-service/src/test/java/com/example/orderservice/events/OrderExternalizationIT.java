/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orderservice.common.AbstractIntegrationTest;
import com.example.orderservice.model.Address;
import com.example.orderservice.model.dtos.OrderDto;
import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import com.example.orderservice.model.response.OrderResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class OrderExternalizationIT extends AbstractIntegrationTest {

    @BeforeEach
    void setUp() {
        testKafkaListenerConfig.reset();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        jdbcTemplate.execute("DELETE FROM jobrunr_jobs");
    }

    @Test
    void shouldExternalizeOrderCreatedEventToKafka() throws Exception {
        OrderRequest orderRequest =
                new OrderRequest(
                        1L,
                        List.of(new OrderItemRequest("Product10", 10, BigDecimal.TEN)),
                        new Address(
                                "Junit Address1",
                                "AddressLine2",
                                "city",
                                "state",
                                "zipCode",
                                "country"));
        mockProductsExistsRequest(true, "PRODUCT10");

        MvcResult mvcResult =
                mockMvc.perform(
                                post("/api/orders")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(jsonMapper.writeValueAsString(orderRequest)))
                        .andExpect(status().isCreated())
                        .andReturn();

        Long orderId =
                jsonMapper
                        .readValue(
                                mvcResult.getResponse().getContentAsString(), OrderResponse.class)
                        .orderId();

        // Verify Kafka message shape
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(
                        () -> {
                            Optional<OrderDto> orderDtoOpt =
                                    testKafkaListenerConfig.getMessageByOrderId(orderId);
                            assertThat(orderDtoOpt).isPresent();
                            OrderDto orderDto = orderDtoOpt.get();
                            assertThat(orderDto.customerId()).isEqualTo(1L);
                            assertThat(orderDto.status()).isEqualTo("NEW");
                        });

        // Verify JobRunr Outbox Event Publication
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () -> {
                            Integer count =
                                    jdbcTemplate.queryForObject(
                                            "SELECT count(*) FROM jobrunr_jobs WHERE state = 'SUCCEEDED' AND jobsignature LIKE '%externalize%'",
                                            Integer.class);
                            assertThat(count).isGreaterThanOrEqualTo(1);
                        });
    }
}
