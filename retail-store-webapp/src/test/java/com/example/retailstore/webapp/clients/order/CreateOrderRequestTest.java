package com.example.retailstore.webapp.clients.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.example.retailstore.webapp.clients.customer.CustomerRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateOrderRequestTest {

    @Test
    void testWithCustomerId() {
        // Setup
        Address deliveryAddress = mock(Address.class);
        CustomerRequest customer = mock(CustomerRequest.class);
        List<OrderItemRequest> items = List.of(new OrderItemRequest("ABC123", 2, new BigDecimal("199.99")));

        CreateOrderRequest request = new CreateOrderRequest(items, customer, deliveryAddress);

        // Execution
        OrderRequestExternal result = request.withCustomerId(123L);

        // Assertions
        assertEquals(123L, result.customerId());
        assertEquals(1, result.items().size());
        assertEquals("ABC123", result.items().getFirst().productCode());
        assertEquals(2, result.items().getFirst().quantity());
        assertEquals(new BigDecimal("199.99"), result.items().getFirst().productPrice());
        assertEquals(deliveryAddress, result.deliveryAddress());
    }
}
