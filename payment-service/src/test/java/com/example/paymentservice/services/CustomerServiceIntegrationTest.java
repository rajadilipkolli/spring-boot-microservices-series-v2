/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import com.example.paymentservice.common.AbstractIntegrationTest;
import com.example.paymentservice.exception.CustomerExistsException;
import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.model.request.CustomerRequest;
import java.math.BigDecimal;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;

class CustomerServiceIntegrationTest extends AbstractIntegrationTest {

    /** Removes existing customers so each test starts with an empty customer table. */
    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
    }

    /**
     * Verifies that when existsByEmail returns false (simulating concurrent insertion race), a
     * duplicate key violation from the database is caught and translated to
     * CustomerExistsException.
     */
    @Test
    void saveCustomerConcurrentDuplicateThrowsCustomerExistsException() {
        CustomersRecord seedCustomer = buildFreshCustomerRecord();
        customerRepository.saveCustomer(seedCustomer);
        Mockito.clearInvocations(customerRepository);

        String normalizedEmail = seedCustomer.getEmail().toLowerCase(Locale.ROOT);
        doReturn(false).when(customerRepository).existsByEmail(normalizedEmail);

        CustomerRequest duplicateRequest =
                new CustomerRequest(
                        seedCustomer.getName(),
                        seedCustomer.getEmail(),
                        "987-654-3210",
                        "456 Other St",
                        "Apt 2",
                        "Otherville",
                        "OS",
                        "54321",
                        "Otherland",
                        BigDecimal.valueOf(50.0));

        assertThatThrownBy(() -> customerService.saveCustomer(duplicateRequest))
                .isInstanceOf(CustomerExistsException.class)
                .hasMessage("Customer with email " + normalizedEmail + " already exists.");

        verify(customerRepository).existsByEmail(normalizedEmail);
        verify(customerRepository)
                .saveCustomer(org.mockito.ArgumentMatchers.any(CustomersRecord.class));

        assertThat(
                        customerDAO.findAll(
                                com.example.paymentservice.jooq.tables.Customers.CUSTOMERS.EMAIL.eq(
                                        normalizedEmail)))
                .hasSize(1);
        CustomersRecord persisted = customerDAO.findByEmail(normalizedEmail).orElseThrow();
        assertThat(persisted.getName()).isEqualTo(seedCustomer.getName());
        assertThat(persisted.getAddressLine1()).isEqualTo(seedCustomer.getAddressLine1());
    }

    /**
     * Verifies that inserting a customer with an invalid/null required field violates database
     * constraints and propagates DataIntegrityViolationException (not CustomerExistsException),
     * wrapping a PSQLException with SQLSTATE 23502.
     */
    @Test
    void saveCustomerNullConstraintThrowsDataIntegrityViolationException() {
        // CustomerRequest validation checks @NotBlank on name, phone, etc.
        // We test database constraint propagation using CustomerService.
        // But CustomerRequest validation fails bean validation if name is null.
        // Note: CustomerRequest record constructor itself allows null, but if bean validation is
        // checked at method level, let's verify if CustomerService has method-level validation.
        // CustomerService class does not have @Validated annotation.
        CustomerRequest requestWithNullName =
                new CustomerRequest(
                        null,
                        "fresh@example.com",
                        "123-456-7890",
                        "123 Test St",
                        null,
                        "Testville",
                        "TS",
                        "12345",
                        "Testland",
                        BigDecimal.valueOf(100.0));

        assertThatThrownBy(() -> customerService.saveCustomer(requestWithNullName))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(CustomerExistsException.class)
                .satisfies(
                        ex -> {
                            PSQLException psqlEx = findCause(ex, PSQLException.class);
                            assertThat((Throwable) psqlEx).isNotNull();
                            assertThat(psqlEx.getSQLState()).isEqualTo("23502");
                        });

        assertThat(customerDAO.findByEmail("fresh@example.com")).isEmpty();
    }

    /**
     * Builds an unsaved customer record for seeding duplicate-insertion tests.
     *
     * @return a customer with valid required fields and no reserved funds
     */
    private CustomersRecord buildFreshCustomerRecord() {
        return new CustomersRecord()
                .setName("Seed User")
                .setEmail("seed@example.com")
                .setPhone("123-456-7890")
                .setAddressLine1("123 Test St")
                .setCity("Testville")
                .setState("TS")
                .setZipCode("12345")
                .setCountry("Testland")
                .setAmountAvailable(BigDecimal.valueOf(100.0))
                .setAmountReserved(BigDecimal.ZERO);
    }

    /**
     * Finds the first exception of the expected type, including the supplied exception itself.
     *
     * @param <T> the expected exception type
     * @param throwable the exception whose cause chain to inspect
     * @param expectedType the exception class to match
     * @return the first matching exception, or null if none is found
     */
    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> expectedType) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (expectedType.isInstance(current)) {
                return expectedType.cast(current);
            }
        }
        return null;
    }
}
