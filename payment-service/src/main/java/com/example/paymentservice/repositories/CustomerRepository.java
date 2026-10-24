/*** Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.OrderField;

public interface CustomerRepository {

    /**
     * Finds a customer by its identifier, including soft-deleted customers. Retained for payment
     * processing.
     *
     * @param id the customer identifier
     * @return the matching record, or empty if absent
     */
    Optional<CustomersRecord> findById(Long id);

    /**
     * Finds an active customer by its identifier.
     *
     * @param id the customer identifier
     * @return the matching active record, or empty if absent or deleted
     */
    Optional<CustomersRecord> findActiveById(Long id);

    /**
     * Finds a customer by an exact name match.
     *
     * @param name the name to look up
     * @return the matching record, or empty if absent
     */
    Optional<CustomersRecord> findByName(String name);

    /**
     * Finds a customer by an exact match against a normalized email address.
     *
     * @param normalizedEmail the lowercase email address to look up
     * @return the matching record, or empty if absent
     */
    Optional<CustomersRecord> findByEmail(String normalizedEmail);

    /**
     * Fetches a sorted slice of customer records.
     *
     * @param offset the number of rows to skip
     * @param limit the maximum number of rows to return
     * @param orderFields the sort expressions to apply
     * @return the requested customer records
     */
    List<CustomersRecord> findAll(int offset, int limit, List<OrderField<?>> orderFields);

    /**
     * Fetches a sorted slice of active customer records.
     *
     * @param offset the number of rows to skip
     * @param limit the maximum number of rows to return
     * @param orderFields the sort expressions to apply
     * @return the requested active customer records
     */
    List<CustomersRecord> findActiveAll(int offset, int limit, List<OrderField<?>> orderFields);

    /**
     * Checks whether a customer has the supplied email address.
     *
     * @param email the normalized email address to match exactly
     * @return whether a matching customer exists
     */
    boolean existsByEmail(String email);

    /** Returns the total number of stored customers. */
    long count();

    /** Returns the total number of active customers. */
    long countActive();

    /**
     * Inserts customer records after normalizing emails and assigning missing IDs and versions.
     *
     * @param customerList the nonempty list of records to insert
     * @return the inserted records with their persisted values
     * @throws java.util.NoSuchElementException if the list is empty
     */
    List<CustomersRecord> saveAll(List<CustomersRecord> customerList);

    /**
     * Inserts a new customer or updates an existing customer using optimistic locking.
     *
     * @param requestCustomer the record to save; a null ID indicates a new customer
     * @return the persisted record, including its version
     * @throws org.springframework.dao.OptimisticLockingFailureException if the existing row is
     *     absent or its version no longer matches
     */
    CustomersRecord saveCustomer(CustomersRecord requestCustomer);

    /**
     * Deletes all customers along with all ledger entries, payment operations, and outbox events.
     *
     * @return the number of deleted customer rows
     */
    int deleteAll();

    /**
     * Deletes the customer with the supplied identifier.
     *
     * @param id the identifier of the customer to delete
     * @return the number of deleted rows
     */
    int deleteById(Long id);

    /**
     * Soft deletes the customer with the supplied identifier if active, incrementing its version
     * without changing balances. Database failures propagate.
     *
     * @param id the identifier of the customer to soft delete
     * @return one if marked deleted, or zero if absent or already deleted
     */
    int softDeleteById(Long id);

    /**
     * Atomically transfers funds from available to reserved balance if available balance is
     * sufficient.
     *
     * @param customerId the customer identifier
     * @param amount the amount to reserve
     * @return true if funds were reserved, false if insufficient funds or customer not found
     */
    boolean reserveFunds(Long customerId, java.math.BigDecimal amount);

    /**
     * Atomically deducts funds from reserved balance upon order confirmation.
     *
     * @param customerId the customer identifier
     * @param amount the amount to capture
     * @return true if funds were captured, false if insufficient reserved balance or customer not
     *     found
     */
    boolean captureFunds(Long customerId, java.math.BigDecimal amount);

    /**
     * Atomically releases funds from reserved back to available balance upon order rollback.
     *
     * @param customerId the customer identifier
     * @param amount the amount to release
     * @return true if funds were released, false if insufficient reserved balance or customer not
     *     found
     */
    boolean releaseFunds(Long customerId, java.math.BigDecimal amount);
}
