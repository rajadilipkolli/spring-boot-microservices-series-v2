package com.example.retailstore.webapp.clients.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

@JsonTest
class CustomerResponseTest {

    @Autowired
    private JacksonTester<CustomerResponse> json;

    /**
     * Verifies a fractional JSON balance is deserialized without losing its decimal value.
     */
    @Test
    void shouldDeserializeAmountAvailableWithoutLoss() throws Exception {
        String json = """
                {
                  "customerId": "123",
                  "name": "Test User",
                  "amountAvailable": 950.50
                }
                """;

        CustomerResponse response = this.json.parseObject(json);

        assertThat(response.amountAvailable()).isEqualByComparingTo(new BigDecimal("950.50"));
    }
}
