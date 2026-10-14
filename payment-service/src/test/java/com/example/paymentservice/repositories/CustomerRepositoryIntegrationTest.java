/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.paymentservice.common.AbstractIntegrationTest;
import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.OptimisticLockingFailureException;

class CustomerRepositoryIntegrationTest extends AbstractIntegrationTest {

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
    }

    /**
     * Verifies that batch insertion preserves supplied versions and initializes null versions to
     * zero.
     *
     * @param version the version to insert, or null to use the initial version
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, 7})
    void saveAllPreservesSuppliedVersions(Integer version) {
        CustomersRecord customer =
                new CustomersRecord()
                        .setName("Batch Customer")
                        .setEmail("batch@example.com")
                        .setPhone("123-456-7890")
                        .setAddressLine1("123 Test St")
                        .setCity("Testville")
                        .setState("TS")
                        .setZipCode("12345")
                        .setCountry("Testland")
                        .setVersion(version);
        int expectedVersion = version == null ? 0 : version;

        List<CustomersRecord> insertedCustomers = customerRepository.saveAll(List.of(customer));

        assertThat(customer.getVersion()).isEqualTo(expectedVersion);
        assertThat(insertedCustomers).hasSize(1);
        assertThat(insertedCustomers.getFirst().getVersion()).isEqualTo(expectedVersion);
        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getVersion())
                .isEqualTo(expectedVersion);
    }

    /**
     * Verifies initial and incremented versions and rejection of an update using a stale version.
     */
    @Test
    void testOptimisticLocking() {
        CustomersRecord customer =
                new CustomersRecord()
                        .setName("Test User")
                        .setEmail("test@example.com")
                        .setAddressLine1("123 Test St")
                        .setAddressLine2("Apt 4B")
                        .setCity("Testville")
                        .setState("TS")
                        .setZipCode("12345")
                        .setCountry("Testland")
                        .setPhone("123-456-7890")
                        .setAmountAvailable(BigDecimal.valueOf(100.0))
                        .setAmountReserved(BigDecimal.ZERO);

        // 1. Insert sets version to 0
        CustomersRecord savedCustomer1 = customerRepository.saveCustomer(customer);
        assertThat(savedCustomer1.getVersion()).isEqualTo(0);

        // 2. Fetch the same customer twice
        CustomersRecord fetchedCustomer1 =
                customerRepository.findById(savedCustomer1.getId()).orElseThrow();
        CustomersRecord fetchedCustomer2 =
                customerRepository.findById(savedCustomer1.getId()).orElseThrow();

        // 3. Update the first fetched instance
        fetchedCustomer1.setAmountAvailable(BigDecimal.valueOf(80.0));
        CustomersRecord updatedCustomer = customerRepository.saveCustomer(fetchedCustomer1);

        // Version should be incremented
        assertThat(updatedCustomer.getVersion()).isEqualTo(1);

        // 4. Attempt to update the second fetched instance (stale update)
        fetchedCustomer2.setAmountAvailable(BigDecimal.valueOf(50.0));
        assertThatThrownBy(() -> customerRepository.saveCustomer(fetchedCustomer2))
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessageContaining("Customer was updated or deleted by another transaction");
    }

    /**
     * Verifies that inserting a duplicate customer with identical name and email fails with
     * DataIntegrityViolationException (specifically DuplicateKeyException) and wraps a
     * PSQLException for the uc_customers_name_email_key constraint.
     */
    @Test
    void saveCustomerDuplicateNameAndEmailThrowsDataIntegrityViolationException() {
        CustomersRecord customer1 =
                new CustomersRecord()
                        .setName("Duplicate User")
                        .setEmail("duplicate@example.com")
                        .setPhone("123-456-7890")
                        .setAddressLine1("123 Test St")
                        .setCity("Testville")
                        .setState("TS")
                        .setZipCode("12345")
                        .setCountry("Testland")
                        .setAmountAvailable(BigDecimal.valueOf(100.0))
                        .setAmountReserved(BigDecimal.ZERO);
        customerRepository.saveCustomer(customer1);

        CustomersRecord customer2 =
                new CustomersRecord()
                        .setName("Duplicate User")
                        .setEmail("duplicate@example.com")
                        .setPhone("987-654-3210")
                        .setAddressLine1("456 Other St")
                        .setCity("Otherville")
                        .setState("OS")
                        .setZipCode("54321")
                        .setCountry("Otherland")
                        .setAmountAvailable(BigDecimal.valueOf(50.0))
                        .setAmountReserved(BigDecimal.ZERO);

        assertThatThrownBy(() -> customerRepository.saveCustomer(customer2))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class)
                .satisfies(
                        ex -> {
                            org.postgresql.util.PSQLException psqlEx =
                                    findCause(ex, org.postgresql.util.PSQLException.class);
                            assertThat((Throwable) psqlEx).isNotNull();
                            assertThat(psqlEx.getSQLState()).isEqualTo("23505");
                            assertThat(psqlEx.getServerErrorMessage()).isNotNull();
                            assertThat(psqlEx.getServerErrorMessage().getConstraint())
                                    .isEqualTo("uc_customers_name_email_key");
                        });

        assertThat(customerDAO.findByEmail("duplicate@example.com")).isPresent();
        assertThat(
                        customerDAO.findAll(
                                com.example.paymentservice.jooq.tables.Customers.CUSTOMERS.EMAIL.eq(
                                        "duplicate@example.com")))
                .hasSize(1);
    }

    /**
     * Verifies that inserting a record with a null name violates the not-null constraint and throws
     * DataIntegrityViolationException wrapping a PSQLException with SQLSTATE 23502.
     */
    @Test
    void saveCustomerNullNameThrowsDataIntegrityViolationException() {
        CustomersRecord customer =
                new CustomersRecord()
                        .setName(null)
                        .setEmail("nullname@example.com")
                        .setPhone("123-456-7890")
                        .setAddressLine1("123 Test St")
                        .setCity("Testville")
                        .setState("TS")
                        .setZipCode("12345")
                        .setCountry("Testland")
                        .setAmountAvailable(BigDecimal.valueOf(100.0))
                        .setAmountReserved(BigDecimal.ZERO);

        assertThatThrownBy(() -> customerRepository.saveCustomer(customer))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .satisfies(
                        ex -> {
                            org.postgresql.util.PSQLException psqlEx =
                                    findCause(ex, org.postgresql.util.PSQLException.class);
                            assertThat((Throwable) psqlEx).isNotNull();
                            assertThat(psqlEx.getSQLState()).isEqualTo("23502");
                        });

        assertThat(customerDAO.findByEmail("nullname@example.com")).isEmpty();
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
