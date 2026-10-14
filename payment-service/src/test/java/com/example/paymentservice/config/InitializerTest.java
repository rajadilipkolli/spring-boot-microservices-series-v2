/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.repositories.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class InitializerTest {

    @Mock private CustomerRepository customerRepository;

    private Initializer initializer;

    /**
     * Creates the initializer with a mock repository and treats the retail customer as already
     * present.
     */
    @BeforeEach
    void setUp() {
        initializer = new Initializer(customerRepository);
        when(customerRepository.existsByEmail("retailstore@gmail.com")).thenReturn(true);
    }

    /**
     * Verifies that startup does not insert the Raja customer when that customer already exists.
     */
    @Test
    void skipsExistingRajaCustomer() {
        when(customerRepository.existsByEmail("rajakolli@gmail.com")).thenReturn(true);

        initializer.run();

        verify(customerRepository, never()).saveCustomer(any(CustomersRecord.class));
    }

    /** Verifies that startup inserts the Raja customer when the email lookup finds no match. */
    @Test
    void createsMissingRajaCustomer() {
        when(customerRepository.existsByEmail("rajakolli@gmail.com")).thenReturn(false);

        initializer.run();

        verify(customerRepository)
                .saveCustomer(
                        argThat(customer -> "rajakolli@gmail.com".equals(customer.getEmail())));
    }

    /**
     * Verifies that a duplicate-key race is tolerated when a second lookup finds the Raja customer.
     */
    @Test
    void acceptsDuplicateKeyOnlyWhenRajaCustomerNowExists() {
        when(customerRepository.existsByEmail("rajakolli@gmail.com")).thenReturn(false, true);
        when(customerRepository.saveCustomer(any(CustomersRecord.class)))
                .thenThrow(new DuplicateKeyException("duplicate customer"));

        assertThatCode(() -> initializer.run()).doesNotThrowAnyException();
        verify(customerRepository, times(2)).existsByEmail("rajakolli@gmail.com");
    }

    /** Verifies that a duplicate-key failure propagates when the Raja customer remains absent. */
    @Test
    void propagatesDuplicateKeyWhenRajaCustomerIsStillMissing() {
        when(customerRepository.existsByEmail("rajakolli@gmail.com")).thenReturn(false);
        DuplicateKeyException failure = new DuplicateKeyException("unrelated duplicate key");
        when(customerRepository.saveCustomer(any(CustomersRecord.class))).thenThrow(failure);

        assertThatThrownBy(() -> initializer.run()).isSameAs(failure);
    }

    /** Verifies that customer data-integrity failures other than duplicate keys propagate. */
    @Test
    void propagatesOtherIntegrityFailures() {
        assertSaveFailurePropagates(new DataIntegrityViolationException("invalid customer"));
    }

    /** Verifies that database access failures during customer creation propagate. */
    @Test
    void propagatesDatabaseFailures() {
        assertSaveFailurePropagates(new DataAccessResourceFailureException("database unavailable"));
    }

    /**
     * Asserts that a failed Raja customer insert propagates the original exception without another
     * lookup.
     *
     * @param failure the repository save failure to simulate
     */
    private void assertSaveFailurePropagates(RuntimeException failure) {
        when(customerRepository.existsByEmail("rajakolli@gmail.com")).thenReturn(false);
        when(customerRepository.saveCustomer(any(CustomersRecord.class))).thenThrow(failure);

        assertThatThrownBy(() -> initializer.run()).isSameAs(failure);
        verify(customerRepository).existsByEmail("rajakolli@gmail.com");
    }
}
