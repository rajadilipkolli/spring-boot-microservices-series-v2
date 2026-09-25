package com.example.retailstore.webapp.clients.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.example.retailstore.webapp.clients.customer.CustomerRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

@JsonTest
class CreateOrderRequestTest {

    @Autowired
    private JacksonTester<CreateOrderRequest> json;

    @Test
    void testDeserialize() throws Exception {
        // Setup
        String jsonContent = """
                {
                    "customer": {
                        "name": "retail",
                        "email": "retail@gmail.com",
                        "phone": "(274) 748-2938"
                    },
                    "deliveryAddress": {
                        "addressLine1": "280 Rick Lakes",
                        "addressLine2": "Arnoldland",
                        "city": "FL 86710",
                        "state": "TS",
                        "zipCode": "500072",
                        "country": "India"
                    },
                    "items": [
                        {
                            "productCode": "P001",
                            "productName": "iPhone 15 Pro",
                            "price": 999.99,
                            "quantity": 1
                        }
                    ]
                }
                """;

        CreateOrderRequest expected = new CreateOrderRequest(
                List.of(new OrderItemRequest("P001", 1, new BigDecimal("999.99"))),
                new CustomerRequest("retail", "retail@gmail.com", "(274) 748-2938", null, null),
                new Address("280 Rick Lakes", "Arnoldland", "FL 86710", "TS", "500072", "India"));
        assertThat(this.json.parse(jsonContent)).isEqualTo(expected);
    }

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
