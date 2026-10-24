/*** Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import static com.example.paymentservice.jooq.tables.Customers.CUSTOMERS;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.conf.Settings;
import org.jooq.conf.SettingsTools;
import org.jooq.exception.DataChangedException;
import org.jooq.impl.DSL;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class CustomerRepositoryImpl implements CustomerRepository {

    private final DSLContext insertDsl;
    private final CustomerDAO customerDAO;

    /**
     * Creates the repository with database access and record mapping, deriving an insert context
     * with optimistic locking disabled.
     *
     * @param dslContext the context for database operations
     * @param customerDAO the DAO for customer lookups and versioned updates
     */
    public CustomerRepositoryImpl(DSLContext dslContext, CustomerDAO customerDAO) {
        this.customerDAO = customerDAO;

        Settings insertSettings =
                SettingsTools.clone(dslContext.configuration().settings())
                        .withExecuteWithOptimisticLocking(false)
                        .withReturnAllOnUpdatableRecord(true)
                        .withInsertUnchangedRecords(false);
        this.insertDsl = DSL.using(dslContext.configuration().derive(insertSettings));
    }

    /** Returns the customer matching the ID, or empty if none exists. */
    @Override
    public Optional<CustomersRecord> findById(Long id) {
        return customerDAO.findById(id);
    }

    /** Returns the active customer matching the ID, or empty if none exists or deleted. */
    @Override
    public Optional<CustomersRecord> findActiveById(Long id) {
        return customerDAO.findActiveById(id);
    }

    /** Returns the customer matching the exact name, or empty if none exists. */
    @Override
    public Optional<CustomersRecord> findByName(String name) {
        return customerDAO.findByName(name);
    }

    /** Returns the customer matching the normalized email, or empty if none exists. */
    @Override
    public Optional<CustomersRecord> findByEmail(String normalizedEmail) {
        return customerDAO.findByEmail(normalizedEmail);
    }

    /** Returns a customer slice using the supplied row offset, limit, and sort expressions. */
    @Override
    public List<CustomersRecord> findAll(int offset, int limit, List<OrderField<?>> orderFields) {
        return customerDAO.findAll(offset, limit, orderFields);
    }

    /**
     * Returns an active customer slice using the supplied row offset, limit, and sort expressions.
     */
    @Override
    public List<CustomersRecord> findActiveAll(
            int offset, int limit, List<OrderField<?>> orderFields) {
        return customerDAO.findActiveAll(offset, limit, orderFields);
    }

    /** Checks for an exact match against the supplied normalized email address. */
    @Override
    public boolean existsByEmail(String email) {
        return customerDAO.existsByEmail(email);
    }

    /** Returns the total customer count reported by the DAO. */
    @Override
    public long count() {
        return customerDAO.count();
    }

    /** Returns the active customer count reported by the DAO. */
    @Override
    public long countActive() {
        return customerDAO.countActive();
    }

    /**
     * Inserts a new customer when ID is null or updates an existing customer whose ID and version
     * match. Lowercases a non-null email on the supplied record. Inserts generate a TSID and
     * initialize the persisted version to one regardless of the supplied version. Updates require a
     * non-null version and increment the stored version. The supplied record's ID and version
     * remain unchanged. Database failures propagate to the caller.
     *
     * @param requestCustomer the customer details to insert or update
     * @return the persisted CustomersRecord returned by the database
     * @throws OptimisticLockingFailureException if an update finds no matching ID and version,
     *     detects a concurrent change, or an insert affects no rows
     * @throws NullPointerException if the supplied record is null or an existing customer's input
     *     version is null
     */
    @Override
    @Transactional
    public CustomersRecord saveCustomer(CustomersRecord requestCustomer) {
        normalizeCustomer(requestCustomer);
        boolean isNew = requestCustomer.getId() == null;
        if (isNew) {
            return getInsertedCustomersRecord(requestCustomer);
        } else {
            return getUpdatedCustomersRecord(requestCustomer);
        }
    }

    /**
     * Inserts all supplied customers in a batch transaction. Returns an empty list if the input is
     * null or empty. Lowercases non-null emails on the input records, preserves supplied IDs, and
     * copies generated TSIDs back to inputs with missing IDs. Persisted versions start at one
     * regardless of supplied versions; input versions remain unchanged. Database failures propagate
     * and roll back the transaction.
     *
     * @param customerList the list of customers to insert
     * @return the reloaded rows in input order, with null entries for IDs absent from the reload
     */
    @Override
    @Transactional
    public List<CustomersRecord> saveAll(List<CustomersRecord> customerList) {
        if (customerList == null || customerList.isEmpty()) {
            return List.of();
        }

        List<CustomersRecord> customersRecordList = new ArrayList<>(customerList.size());
        for (var customer : customerList) {
            normalizeCustomer(customer);
            CustomersRecord record = insertDsl.newRecord(CUSTOMERS, customer);
            record.reset(CUSTOMERS.VERSION);
            customersRecordList.add(record);
        }

        insertDsl.batchInsert(customersRecordList).execute();

        List<Long> assignedIds = new ArrayList<>(customersRecordList.size());
        for (int i = 0; i < customersRecordList.size(); i++) {
            Long assignedId = customersRecordList.get(i).getId();
            customerList.get(i).setId(assignedId);
            assignedIds.add(assignedId);
        }

        Map<Long, CustomersRecord> byId =
                insertDsl
                        .selectFrom(CUSTOMERS)
                        .where(CUSTOMERS.ID.in(assignedIds))
                        .fetchMap(CUSTOMERS.ID, CustomersRecord.class);
        return assignedIds.stream().map(byId::get).toList();
    }

    /** Deletes the customer by ID and returns the number of affected rows. */
    @Override
    @Transactional
    public int deleteById(Long id) {
        return customerDAO.deleteById(id);
    }

    /**
     * Soft deletes the customer with the supplied identifier if active.
     *
     * @param id the identifier of the customer to soft delete
     * @return the number of updated rows
     */
    @Override
    @Transactional
    public int softDeleteById(Long id) {
        return customerDAO.softDeleteById(id);
    }

    /**
     * Deletes all ledger entries, payment operations, outbox events, and customers through the DAO
     * in one transaction. Database failures propagate.
     *
     * @return the number of deleted customer rows
     */
    @Override
    public int deleteAll() {
        return customerDAO.deleteAll();
    }

    /**
     * Delegates the conditional transfer from available to reserved customer balance to the DAO.
     */
    @Override
    public boolean reserveFunds(Long customerId, BigDecimal amount) {
        return customerDAO.reserveFunds(customerId, amount);
    }

    /** Delegates the conditional deduction from reserved customer balance to the DAO. */
    @Override
    public boolean captureFunds(Long customerId, BigDecimal amount) {
        return customerDAO.captureFunds(customerId, amount);
    }

    /**
     * Delegates the conditional transfer from reserved to available customer balance to the DAO.
     */
    @Override
    public boolean releaseFunds(Long customerId, BigDecimal amount) {
        return customerDAO.releaseFunds(customerId, amount);
    }

    /** Lowercases a non-null email on the supplied record using the root locale. */
    private void normalizeCustomer(CustomersRecord customer) {
        if (customer.getEmail() != null) {
            customer.setEmail(customer.getEmail().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * Updates the stored customer only when its version matches the supplied version, returning the
     * updated record with an incremented version. The supplied record is unchanged. Database
     * failures propagate, except that jOOQ concurrent-change failures become optimistic locking
     * failures.
     *
     * @throws OptimisticLockingFailureException if the customer is missing, the version differs, no
     *     rows are updated, or jOOQ detects a concurrent change
     * @throws NullPointerException if the input version is null for an existing customer
     */
    private CustomersRecord getUpdatedCustomersRecord(CustomersRecord requestCustomer) {
        CustomersRecord existingRecord =
                customerDAO
                        .findById(requestCustomer.getId())
                        .orElseThrow(
                                () ->
                                        new OptimisticLockingFailureException(
                                                "Customer was updated or deleted by another transaction"));

        int expectedVersion = requestCustomer.getVersion();
        if (!existingRecord.getVersion().equals(expectedVersion)) {
            throw new OptimisticLockingFailureException(
                    "Customer was updated or deleted by another transaction");
        }

        mapCustomerWithRecord(existingRecord, requestCustomer);

        try {
            int updatedRows = existingRecord.update();
            if (updatedRows == 0) {
                throw new OptimisticLockingFailureException(
                        "Customer was updated or deleted by another transaction");
            }
            return existingRecord.into(CustomersRecord.class);
        } catch (DataChangedException e) {
            throw new OptimisticLockingFailureException(
                    "Customer was updated or deleted by another transaction", e);
        }
    }

    /**
     * Copies customer details and balances while preserving the target record's ID and version.
     *
     * @param existingRecord the persisted record to update
     * @param requestCustomer the replacement details and balances
     */
    private void mapCustomerWithRecord(
            CustomersRecord existingRecord, CustomersRecord requestCustomer) {
        existingRecord
                .setAmountAvailable(requestCustomer.getAmountAvailable())
                .setAmountReserved(requestCustomer.getAmountReserved())
                .setName(requestCustomer.getName())
                .setAddressLine1(requestCustomer.getAddressLine1())
                .setAddressLine2(requestCustomer.getAddressLine2())
                .setCity(requestCustomer.getCity())
                .setState(requestCustomer.getState())
                .setZipCode(requestCustomer.getZipCode())
                .setCountry(requestCustomer.getCountry())
                .setEmail(requestCustomer.getEmail())
                .setPhone(requestCustomer.getPhone());
    }

    /**
     * Inserts a copy of the supplied customer, generating a missing ID and initializing the
     * persisted version to one regardless of the supplied version. Returns the persisted record
     * without changing the supplied record. Database failures propagate to the caller.
     *
     * @throws OptimisticLockingFailureException if the insert affects no rows
     */
    private CustomersRecord getInsertedCustomersRecord(CustomersRecord requestCustomer) {
        CustomersRecord customersRecord = insertDsl.newRecord(CUSTOMERS, requestCustomer);
        customersRecord.reset(CUSTOMERS.VERSION);
        int inserted = customersRecord.insert();
        if (inserted == 0) {
            throw new OptimisticLockingFailureException(
                    "Customer was inserted by another transaction");
        }
        return customersRecord.into(CustomersRecord.class);
    }
}
