/*** Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import static com.example.paymentservice.jooq.tables.Customers.CUSTOMERS;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.mapper.CustomerMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    private final DSLContext dslContext;
    private final DSLContext insertDsl;
    private final CustomerDAO customerDAO;
    private final CustomerMapper customerMapper;

    /**
     * Creates the repository with database access and record mapping, deriving an insert-specific
     * DSLContext that enables insert record versioning while disabling optimistic locking on
     * inserts.
     *
     * @param dslContext the context for database operations
     * @param customerDAO the DAO for customer lookups and versioned updates
     * @param customerMapper the mapper for updating persisted customer details
     */
    public CustomerRepositoryImpl(
            DSLContext dslContext, CustomerDAO customerDAO, CustomerMapper customerMapper) {
        this.dslContext = dslContext;
        this.customerDAO = customerDAO;
        this.customerMapper = customerMapper;

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

    /**
     * Inserts a new customer when ID is null or updates an existing customer whose ID and version
     * match. For inserts, lowercases non-null emails, lets jOOQ assign the initial version when the
     * input version is null, stores supplied non-null versions unchanged, and mutates the input
     * record with the persisted ID and version. For updates, checks that the stored version matches
     * the input version, increments the stored version, and leaves the supplied instance's version
     * unchanged.
     *
     * @param requestCustomer the customer details to insert or update
     * @return the persisted CustomersRecord returned by the database
     * @throws OptimisticLockingFailureException if an update finds no row matching the supplied ID
     *     and version
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

        customerMapper.mapCustomerWithRecord(existingRecord, requestCustomer);

        try {
            int updatedRows = customerDAO.save(existingRecord);
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

    /**
     * Inserts all supplied customers in a batch transaction. Returns an empty list if the input is
     * empty. Lowercases non-null emails, preserves supplied IDs while assigning TSIDs to records
     * with missing IDs, stores supplied non-null versions unchanged while letting jOOQ assign the
     * initial version for null versions, and mutates the input records with the persisted versions.
     *
     * @param customerList the list of customers to insert
     * @return the list of persisted CustomersRecord rows in input order
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

    /** Deletes all customer rows in the current transaction. */
    @Override
    @Transactional
    public void deleteAll() {
        dslContext.deleteFrom(CUSTOMERS).execute();
    }

    /** Lowercases a non-null email on the supplied record using the root locale. */
    private void normalizeCustomer(CustomersRecord customer) {
        if (customer.getEmail() != null) {
            customer.setEmail(customer.getEmail().toLowerCase(Locale.ROOT));
        }
    }
}
