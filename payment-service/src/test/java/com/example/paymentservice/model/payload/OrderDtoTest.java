/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.model.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertNotNull(json);
        assertTrue(json.contains("\"orderId\":1"));
        assertTrue(json.contains("\"customerId\":123"));
        assertTrue(json.contains("\"status\":\"NEW\""));
        assertTrue(json.contains("\"source\":\"TEST_SOURCE\""));
    }

    @Test
    void testDeserialization() throws Exception {
        String json =
                """
                {"orderId":1,"customerId":1,"status":"NEW","source":"WEB","items":[{"itemId":1,"productId":"P001","quantity":1,"productPrice":999.99,"price":999.99},{"itemId":51,"productId":"P005","quantity":2,"productPrice":249.99,"price":499.98}]}
                """;
        OrderDto order = jsonMapper.parseObject(json);
        assertNotNull(order);
        assertEquals(1L, order.orderId());
        assertEquals(1L, order.customerId());
        assertEquals("NEW", order.status());
        assertEquals("WEB", order.source());
        assertNotNull(order.items());
        assertEquals(2, order.items().size());
    }
}
