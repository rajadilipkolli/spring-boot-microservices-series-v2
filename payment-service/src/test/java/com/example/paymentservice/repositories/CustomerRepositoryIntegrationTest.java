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
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

class CustomerRepositoryIntegrationTest extends AbstractIntegrationTest {

    @BeforeEach
    void setUp() {
        customerRepository.deleteAll();
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
        assertThat(savedCustomer1.getVersion()).isEqualTo(1);

        // 2. Fetch the same customer twice
        CustomersRecord fetchedCustomer1 =
                customerRepository.findById(savedCustomer1.getId()).orElseThrow();
        CustomersRecord fetchedCustomer2 =
                customerRepository.findById(savedCustomer1.getId()).orElseThrow();

        // 3. Update the first fetched instance
        fetchedCustomer1.setAmountAvailable(BigDecimal.valueOf(80.0));
        CustomersRecord updatedCustomer = customerRepository.saveCustomer(fetchedCustomer1);

        // Version should be incremented
        assertThat(updatedCustomer.getVersion()).isEqualTo(2);

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
                .isInstanceOf(DuplicateKeyException.class)
                .satisfies(
                        ex -> {
                            PSQLException psqlEx = findCause(ex, PSQLException.class);
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
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(
                        ex -> {
                            PSQLException psqlEx = findCause(ex, PSQLException.class);
                            assertThat((Throwable) psqlEx).isNotNull();
                            assertThat(psqlEx.getSQLState()).isEqualTo("23502");
                        });

        assertThat(customerDAO.findByEmail("nullname@example.com")).isEmpty();
    }

    /**
     * Verifies that multi-record saveAll correctly handles null and supplied IDs, omitted versions,
     * explicit null versions, and supplied versions 0 and 7. Verifies returned order and email
     * lowercasing.
     */
    @Test
    void saveAllMultiRecordWithVariedIdsAndVersions() {
        long suppliedId = 999999999L;
        CustomersRecord customer1 =
                new CustomersRecord()
                        .setName("Customer One")
                        .setEmail("ONE@EXAMPLE.COM")
                        .setPhone("111-111-1111")
                        .setAddressLine1("1st St")
                        .setCity("City1")
                        .setState("ST1")
                        .setZipCode("11111")
                        .setCountry("Country1");
        // customer1: omitted version (null) and null ID

        CustomersRecord customer2 =
                new CustomersRecord()
                        .setName("Customer Two")
                        .setEmail("TWO@EXAMPLE.COM")
                        .setPhone("222-222-2222")
                        .setAddressLine1("2nd St")
                        .setCity("City2")
                        .setState("ST2")
                        .setZipCode("22222")
                        .setCountry("Country2")
                        .setVersion(null);
        // customer2: explicit setVersion(null) and null ID

        CustomersRecord customer3 =
                new CustomersRecord()
                        .setId(suppliedId)
                        .setName("Customer Three")
                        .setEmail("THREE@EXAMPLE.COM")
                        .setPhone("333-333-3333")
                        .setAddressLine1("3rd St")
                        .setCity("City3")
                        .setState("ST3")
                        .setZipCode("33333")
                        .setCountry("Country3")
                        .setVersion(0);
        // customer3: supplied ID and supplied version 0

        CustomersRecord customer4 =
                new CustomersRecord()
                        .setName("Customer Four")
                        .setEmail("FOUR@EXAMPLE.COM")
                        .setPhone("444-444-4444")
                        .setAddressLine1("4th St")
                        .setCity("City4")
                        .setState("ST4")
                        .setZipCode("44444")
                        .setCountry("Country4")
                        .setVersion(7);
        // customer4: null ID and supplied version 7

        List<CustomersRecord> inputList = List.of(customer1, customer2, customer3, customer4);
        List<CustomersRecord> savedList = customerRepository.saveAll(inputList);

        // Assert assigned or supplied IDs on input records
        assertThat(customer1.getId()).isNotNull();
        assertThat(customer2.getId()).isNotNull();
        assertThat(customer3.getId()).isEqualTo(suppliedId);
        assertThat(customer4.getId()).isNotNull();

        // Assert returned rows follow input order and have lowercased emails and correct versions
        assertThat(savedList).hasSize(4);
        assertThat(savedList.getFirst().getId()).isEqualTo(customer1.getId());
        assertThat(savedList.get(0).getEmail()).isEqualTo("one@example.com");
        assertThat(savedList.get(0).getVersion()).isEqualTo(1);

        assertThat(savedList.get(1).getId()).isEqualTo(customer2.getId());
        assertThat(savedList.get(1).getEmail()).isEqualTo("two@example.com");
        assertThat(savedList.get(1).getVersion()).isEqualTo(1);

        assertThat(savedList.get(2).getId()).isEqualTo(suppliedId);
        assertThat(savedList.get(2).getEmail()).isEqualTo("three@example.com");
        assertThat(savedList.get(2).getVersion()).isEqualTo(1);

        assertThat(savedList.get(3).getId()).isEqualTo(customer4.getId());
        assertThat(savedList.get(3).getEmail()).isEqualTo("four@example.com");
        assertThat(savedList.get(3).getVersion()).isEqualTo(1);

        // Assert reloaded records from database
        assertThat(customerRepository.findById(customer1.getId()).orElseThrow().getVersion())
                .isEqualTo(1);
        assertThat(customerRepository.findById(customer2.getId()).orElseThrow().getVersion())
                .isEqualTo(1);
        assertThat(customerRepository.findById(customer3.getId()).orElseThrow().getVersion())
                .isEqualTo(1);
        assertThat(customerRepository.findById(customer4.getId()).orElseThrow().getVersion())
                .isEqualTo(1);
    }

    /**
     * Verifies saveCustomer insert for omitted version, explicit null version, and supplied version
     * 7.
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {7})
    void saveCustomerInsertWithVariedVersions(Integer initialVersion) {
        CustomersRecord customer =
                new CustomersRecord()
                        .setName("Single Customer " + initialVersion)
                        .setEmail("single" + initialVersion + "@example.com")
                        .setPhone("123-456-7890")
                        .setAddressLine1("123 Test St")
                        .setCity("Testville")
                        .setState("TS")
                        .setZipCode("12345")
                        .setCountry("Testland");

        customer.setVersion(initialVersion);

        CustomersRecord savedCustomer = customerRepository.saveCustomer(customer);

        // Assert version on input, returned, and reloaded records
        assertThat(savedCustomer.getVersion()).isEqualTo(1);
        assertThat(customerRepository.findById(savedCustomer.getId()).orElseThrow().getVersion())
                .isEqualTo(1);
    }

    /**
     * Verifies that when a batch in saveAll fails (e.g. duplicate name and email), the transaction
     * rolls back and no row from the call persists.
     */
    @Test
    void saveAllFailureRollsBackEntireBatch() {
        CustomersRecord customer1 =
                new CustomersRecord()
                        .setName("Batch User")
                        .setEmail("duplicate_batch@example.com")
                        .setPhone("111-111-1111")
                        .setAddressLine1("1st St")
                        .setCity("City1")
                        .setState("ST1")
                        .setZipCode("11111")
                        .setCountry("Country1");

        CustomersRecord customer2 =
                new CustomersRecord()
                        .setName("Batch User")
                        .setEmail("duplicate_batch@example.com")
                        .setPhone("222-222-2222")
                        .setAddressLine1("2nd St")
                        .setCity("City2")
                        .setState("ST2")
                        .setZipCode("22222")
                        .setCountry("Country2");

        assertThatThrownBy(() -> customerRepository.saveAll(List.of(customer1, customer2)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThat(customerDAO.findByEmail("duplicate_batch@example.com")).isEmpty();
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
