/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.model.payload;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

@JsonTest
class OrderDtoTest {

    @Autowired private JacksonTester<OrderDto> jsonMapper;

    @Test
    void testSerialization() throws Exception {
        OrderItemDto item = new OrderItemDto(1L, "Product A", 2, BigDecimal.TEN);
        OrderDto order = new OrderDto(1L, 123L, "NEW", "TEST_SOURCE", List.of(item));

        String json = jsonMapper.write(order).getJson();
        assertThat(json).isNotNull();
        assertThat(json.contains("\"orderId\":1")).isTrue();
        assertThat(json.contains("\"customerId\":123")).isTrue();
        assertThat(json.contains("\"status\":\"NEW\"")).isTrue();
        assertThat(json.contains("\"source\":\"TEST_SOURCE\"")).isTrue();
    }

    @Test
    void testDeserialization() throws Exception {
        String json =
                """
                {"orderId":1,"customerId":1,"status":"NEW","source":"WEB","items":[{"itemId":1,"productId":"P001","quantity":1,"productPrice":999.99,"price":999.99},{"itemId":51,"productId":"P005","quantity":2,"productPrice":249.99,"price":499.98}]}
                """;
        OrderDto order = jsonMapper.parseObject(json);
        assertThat(order).isNotNull();
        assertThat(order.orderId()).isEqualTo(1L);
        assertThat(order.customerId()).isEqualTo(1L);
        assertThat(order.status()).isEqualTo("NEW");
        assertThat(order.source()).isEqualTo("WEB");
        assertThat(order.items()).isNotNull();
        assertThat(order.items().size()).isEqualTo(2);
    }
}
