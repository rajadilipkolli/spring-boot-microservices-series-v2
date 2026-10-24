/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.model.payload;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

@JsonTest
class OrderDtoTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Autowired private JacksonTester<OrderDto> jsonMapper;

    /** Verifies that validation rejects an order containing a null item. */
    @Test
    void whenItemIsNull_thenValidationFails() {
        List<OrderDto.OrderItemDto> items = new ArrayList<>();
        items.add(null);
        OrderDto orderDto = new OrderDto(1L, 100L, "NEW", "WEB", items);

        Set<ConstraintViolation<OrderDto>> violations = validator.validate(orderDto);

        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).isEqualTo("must not be null");
    }

    /**
     * Verifies that order and item IDs serialize as JSON strings while status and source are
     * preserved.
     */
    @Test
    void testSerialization() throws IOException {
        OrderDto.OrderItemDto item = new OrderDto.OrderItemDto(1L, "Product A", 2, BigDecimal.TEN);
        OrderDto order = new OrderDto(1L, 123L, "NEW", "TEST_SOURCE", List.of(item));

        String json = jsonMapper.write(order).getJson();
        assertThat(json).isNotNull();
        assertThat(json).contains("\"itemId\":\"1\"");
        assertThat(json.contains("\"orderId\":\"1\"")).isTrue();
        assertThat(json.contains("\"customerId\":\"123\"")).isTrue();
        assertThat(json.contains("\"status\":\"NEW\"")).isTrue();
        assertThat(json.contains("\"source\":\"TEST_SOURCE\"")).isTrue();
    }

    /**
     * Verifies that an order payload with numeric IDs deserializes with its metadata and items
     * intact.
     */
    @Test
    void testDeserialization() throws IOException {
        String json =
                """
                {"orderId":1,"customerId":1,"status":"NEW","source":"WEB","items":[{"itemId":1,"productId":"P001","quantity":1,"productPrice":999.99,"price":999.99},{"itemId":51,"productId":"P005","quantity":2,"productPrice":249.99,"price":499.98}]}
                """;
        OrderDto order = jsonMapper.parse(json).getObject();
        assertThat(order).isNotNull();
        assertThat(order.orderId()).isEqualTo(1L);
        assertThat(order.customerId()).isEqualTo(1L);
        assertThat(order.status()).isEqualTo("NEW");
        assertThat(order.source()).isEqualTo("WEB");
        assertThat(order.items()).isNotNull();
        assertThat(order.items().size()).isEqualTo(2);
    }

    /**
     * Verifies that numeric and string JSON IDs above JavaScript's safe integer limit retain their
     * exact long values.
     *
     * @param stringIds whether the payload encodes IDs as strings instead of numbers
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptsNumericAndStringIdsWithoutPrecisionLoss(boolean stringIds) throws IOException {
        String id = stringIds ? "\"9007199254740993\"" : "9007199254740993";
        String json =
                """
                {"orderId":%s,"customerId":%s,"status":"NEW","source":"WEB",
                 "items":[{"itemId":%s,"productId":"P001","quantity":1,"productPrice":10}]}
                """
                        .formatted(id, id, id);

        OrderDto order = jsonMapper.parse(json).getObject();
        assertThat(order.orderId()).isEqualTo(9007199254740993L);
        assertThat(order.customerId()).isEqualTo(9007199254740993L);
        assertThat(order.items().getFirst().itemId()).isEqualTo(9007199254740993L);
    }

    @Test
    void responseCopiesAndJsonPreserveAttemptId() throws IOException {
        var order = new OrderDto(1L, 2L, "NEW", null, List.of(), "attempt-1");
        var response = order.withSource("PARTICIPANT").withStatus("ACCEPT");
        var decoded = jsonMapper.parse(jsonMapper.write(response).getJson()).getObject();
        assertThat(decoded.attemptId()).isEqualTo("attempt-1");
    }
}
