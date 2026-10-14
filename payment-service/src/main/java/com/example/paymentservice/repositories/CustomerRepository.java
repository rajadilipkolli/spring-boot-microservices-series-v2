/*** Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.OrderField;

public interface CustomerRepository {

    /**
     * Finds a customer by its identifier.
     *
     * @param id the customer identifier
     * @return the matching record, or empty if absent
     */
    Optional<CustomersRecord> findById(Long id);

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
     * Checks whether a customer has the supplied email address.
     *
     * @param email the normalized email address to match exactly
     * @return whether a matching customer exists
     */
    boolean existsByEmail(String email);

    /** Returns the total number of stored customers. */
    long count();

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

    void deleteAll();

    /**
     * Deletes the customer with the supplied identifier.
     *
     * @param id the identifier of the customer to delete
     * @return the number of deleted rows
     */
    int deleteById(Long id);
}
