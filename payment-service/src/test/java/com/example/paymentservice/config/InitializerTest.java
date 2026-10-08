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

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.repositories.CustomerRepository;
import java.util.Optional;
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

    @BeforeEach
    void setUp() {
        initializer = new Initializer(customerRepository);
        when(customerRepository.findByEmail("retail@gmail.com"))
                .thenReturn(Optional.of(new Customer()));
    }

    @Test
    void skipsExistingRajaCustomer() {
        when(customerRepository.findByEmail("rajakolli@gmail.com"))
                .thenReturn(Optional.of(new Customer()));

        initializer.run();

        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void createsMissingRajaCustomer() {
        when(customerRepository.findByEmail("rajakolli@gmail.com")).thenReturn(Optional.empty());

        initializer.run();

        verify(customerRepository)
                .save(argThat(customer -> "rajakolli@gmail.com".equals(customer.getEmail())));
    }

    @Test
    void acceptsDuplicateKeyOnlyWhenRajaCustomerNowExists() {
        when(customerRepository.findByEmail("rajakolli@gmail.com"))
                .thenReturn(Optional.empty(), Optional.of(new Customer()));
        when(customerRepository.save(any(Customer.class)))
                .thenThrow(new DuplicateKeyException("duplicate customer"));

        assertThatCode(() -> initializer.run()).doesNotThrowAnyException();
        verify(customerRepository, times(2)).findByEmail("rajakolli@gmail.com");
    }

    @Test
    void propagatesDuplicateKeyWhenRajaCustomerIsStillMissing() {
        when(customerRepository.findByEmail("rajakolli@gmail.com")).thenReturn(Optional.empty());
        DuplicateKeyException failure = new DuplicateKeyException("unrelated duplicate key");
        when(customerRepository.save(any(Customer.class))).thenThrow(failure);

        assertThatThrownBy(() -> initializer.run()).isSameAs(failure);
    }

    @Test
    void propagatesOtherIntegrityFailures() {
        assertSaveFailurePropagates(new DataIntegrityViolationException("invalid customer"));
    }

    @Test
    void propagatesDatabaseFailures() {
        assertSaveFailurePropagates(new DataAccessResourceFailureException("database unavailable"));
    }

    private void assertSaveFailurePropagates(RuntimeException failure) {
        when(customerRepository.findByEmail("rajakolli@gmail.com")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenThrow(failure);

        assertThatThrownBy(() -> initializer.run()).isSameAs(failure);
        verify(customerRepository).findByEmail("rajakolli@gmail.com");
    }
}
