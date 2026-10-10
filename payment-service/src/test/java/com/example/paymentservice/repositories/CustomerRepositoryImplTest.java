/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.paymentservice.common.AbstractIntegrationTest;
import com.example.paymentservice.entities.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;

class CustomerRepositoryImplTest extends AbstractIntegrationTest {

    @Autowired private CustomerRepository customerRepository;

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
    }

    @Test
    void testOptimisticLocking() {
        Customer customer =
                new Customer()
                        .setName("Test User")
                        .setEmail("test@example.com")
                        .setAddress("123 Test St")
                        .setPhone("123-456-7890")
                        .setAmountAvailable(100.0)
                        .setAmountReserved(0.0);

        // 1. Insert sets version to 0
        Customer savedCustomer1 = customerRepository.save(customer);
        assertThat(savedCustomer1.getVersion()).isEqualTo(0);

        // 2. Fetch the same customer twice
        Customer fetchedCustomer1 =
                customerRepository.findById(savedCustomer1.getId()).orElseThrow();
        Customer fetchedCustomer2 =
                customerRepository.findById(savedCustomer1.getId()).orElseThrow();

        // 3. Update the first fetched instance
        fetchedCustomer1.setAmountAvailable(80.0);
        Customer updatedCustomer = customerRepository.save(fetchedCustomer1);

        // Version should be incremented
        assertThat(updatedCustomer.getVersion()).isEqualTo(1);

        // 4. Attempt to update the second fetched instance (stale update)
        fetchedCustomer2.setAmountAvailable(50.0);
        assertThatThrownBy(() -> customerRepository.save(fetchedCustomer2))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessageContaining("Customer was updated or deleted by another transaction");
    }
}
