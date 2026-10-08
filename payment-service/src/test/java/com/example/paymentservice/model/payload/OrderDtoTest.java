/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.model.payload;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class OrderDtoTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void testSerialization() throws Exception {
        OrderItemDto item = new OrderItemDto(1L, "Product A", 2, BigDecimal.TEN);
        OrderDto order = new OrderDto(1L, 123L, "NEW", "TEST_SOURCE", List.of(item));

        String json = jsonMapper.writeValueAsString(order);
        assertThat(json).isNotNull();
        assertThat(json).contains("\"itemId\":\"1\"");
        assertThat(json.contains("\"orderId\":\"1\"")).isTrue();
        assertThat(json.contains("\"customerId\":\"123\"")).isTrue();
        assertThat(json.contains("\"status\":\"NEW\"")).isTrue();
        assertThat(json.contains("\"source\":\"TEST_SOURCE\"")).isTrue();
    }

    @Test
    void testDeserialization() throws Exception {
        String json =
                """
                {"orderId":1,"customerId":1,"status":"NEW","source":"WEB","items":[{"itemId":1,"productId":"P001","quantity":1,"productPrice":999.99,"price":999.99},{"itemId":51,"productId":"P005","quantity":2,"productPrice":249.99,"price":499.98}]}
                """;
        OrderDto order = jsonMapper.readValue(json, OrderDto.class);
        assertThat(order).isNotNull();
        assertThat(order.orderId()).isEqualTo(1L);
        assertThat(order.customerId()).isEqualTo(1L);
        assertThat(order.status()).isEqualTo("NEW");
        assertThat(order.source()).isEqualTo("WEB");
        assertThat(order.items()).isNotNull();
        assertThat(order.items().size()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptsNumericAndStringIdsWithoutPrecisionLoss(boolean stringIds) {
        String id = stringIds ? "\"9007199254740993\"" : "9007199254740993";
        String json =
                """
                {"orderId":%s,"customerId":%s,"status":"NEW","source":"WEB",
                 "items":[{"itemId":%s,"productId":"P001","quantity":1,"productPrice":10}]}
                """
                        .formatted(id, id, id);

        OrderDto order = JsonMapper.builder().build().readValue(json, OrderDto.class);
        assertThat(order.orderId()).isEqualTo(9007199254740993L);
        assertThat(order.customerId()).isEqualTo(9007199254740993L);
        assertThat(order.items().getFirst().itemId()).isEqualTo(9007199254740993L);
    }
}
