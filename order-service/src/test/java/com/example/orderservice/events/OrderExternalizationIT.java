/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.events;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.common.AbstractIntegrationTest;
import com.example.orderservice.model.Address;
import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class OrderExternalizationIT extends AbstractIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        testKafkaListenerConfig.reset();
        orderItemRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        jdbcTemplate.execute("DELETE FROM event_publication");
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

        mockMvc.perform(
                        post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated());

        // Verify Kafka message shape
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () -> {
                            OrderDto orderDto = testKafkaListenerConfig.pollPayload(1, SECONDS);
                            assertThat(orderDto).isNotNull();
                            assertThat(orderDto.customerId()).isEqualTo(1L);
                            assertThat(orderDto.status()).isEqualTo("NEW");
                        });

        // Verify Modulith Outbox Event Publication
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () -> {
                            Integer count =
                                    jdbcTemplate.queryForObject(
                                            "SELECT count(*) FROM event_publication WHERE completion_date IS NOT NULL",
                                            Integer.class);
                            assertThat(count).isGreaterThanOrEqualTo(1);
                        });
    }
}
