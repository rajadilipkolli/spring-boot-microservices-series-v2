/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.paymentservice.exception.CustomerExistsException;
import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.mapper.CustomerMapper;
import com.example.paymentservice.model.request.CustomerRequest;
import com.example.paymentservice.repositories.CustomerRepository;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;

    @Mock private CustomerMapper customerMapper;

    @InjectMocks private CustomerService customerService;

    private final CustomerRequest request =
            new CustomerRequest(
                    "Customer",
                    "CUSTOMER@EXAMPLE.COM",
                    "1234567890",
                    "123 Test St",
                    null,
                    "Testville",
                    "TS",
                    "12345",
                    "Testland",
                    BigDecimal.TEN);

    /**
     * Verifies that an existing normalized email is rejected before a customer insert is attempted.
     */
    @Test
    void rejectsExistingEmailBeforeInsert() {
        given(customerRepository.existsByEmail("customer@example.com")).willReturn(true);

        assertThatThrownBy(() -> customerService.saveCustomer(request))
                .isInstanceOf(CustomerExistsException.class)
                .hasMessage("Customer with email customer@example.com already exists.");

        verify(customerRepository, never()).saveCustomer(any(CustomersRecord.class));
    }

    /**
     * Verifies that an available email permits creation and returns the persisted customer details.
     */
    @Test
    void createsCustomerWhenEmailIsAvailable() {
        CustomersRecord saved =
                request.toCustomersRecord().setId(1L).setEmail("customer@example.com");
        given(customerRepository.saveCustomer(any(CustomersRecord.class))).willReturn(saved);

        var response = customerService.saveCustomer(request);

        assertThat(response.customerId()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("customer@example.com");
        verify(customerRepository).existsByEmail("customer@example.com");
    }

    /** Verifies that a concurrent email-constraint violation becomes a customer conflict. */
    @Test
    void translatesEmailConstraintViolationAfterPreInsertCheck() {
        DataIntegrityViolationException failure =
                new DuplicateKeyException(
                        "insert failed",
                        new DataAccessException(
                                "jOOQ insert failed",
                                postgresFailure("23505", "uc_customers_name_email_key")));
        given(customerRepository.saveCustomer(any(CustomersRecord.class))).willThrow(failure);

        assertThatThrownBy(() -> customerService.saveCustomer(request))
                .isInstanceOf(CustomerExistsException.class)
                .hasMessage("Customer with email customer@example.com already exists.");

        verify(customerRepository).existsByEmail("customer@example.com");
    }

    /**
     * Verifies that failures unrelated to the customer email constraint propagate unchanged.
     *
     * @param failure the persistence failure supplied by the method source
     */
    @ParameterizedTest
    @MethodSource("unrelatedFailures")
    void rethrowsUnrelatedSaveFailureUnchanged(RuntimeException failure) {
        given(customerRepository.saveCustomer(any(CustomersRecord.class))).willThrow(failure);

        assertThatThrownBy(() -> customerService.saveCustomer(request)).isSameAs(failure);
    }

    /**
     * Supplies integrity and access failures that must retain their original exception identity.
     */
    private static Stream<RuntimeException> unrelatedFailures() {
        return Stream.of(
                new DuplicateKeyException("unidentified duplicate"),
                new DataIntegrityViolationException("invalid customer"),
                new DataIntegrityViolationException(
                        "duplicate id", postgresFailure("23505", "customers_pkey")),
                new DataIntegrityViolationException(
                        "similar constraint",
                        postgresFailure("23505", "uc_customers_name_email_key_other")),
                new DataIntegrityViolationException(
                        "check violation", postgresFailure("23514", "uc_customers_name_email_key")),
                new DataIntegrityViolationException(
                        "unknown constraint", postgresFailure("23505", null)),
                new DataIntegrityViolationException(
                        "no server metadata",
                        new PSQLException(
                                "uc_customers_name_email_key", PSQLState.UNIQUE_VIOLATION)),
                new DataAccessResourceFailureException("database unavailable"));
    }

    /**
     * Creates a PostgreSQL failure with server metadata for constraint-translation tests.
     *
     * @param sqlState the SQL state to report
     * @param constraint the constraint name, or null to omit it from server metadata
     * @return the simulated PostgreSQL exception
     */
    private static PSQLException postgresFailure(String sqlState, String constraint) {
        String constraintField = constraint == null ? "" : "n" + constraint + "\0";
        return new PSQLException(
                new ServerErrorMessage(
                        "SERROR\0C" + sqlState + "\0" + constraintField + "Mfailure\0\0"));
    }
}
