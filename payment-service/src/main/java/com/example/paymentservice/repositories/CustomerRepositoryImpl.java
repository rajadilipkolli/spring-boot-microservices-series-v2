/*** Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import static com.example.paymentservice.jooq.tables.Customers.CUSTOMERS;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.mapper.CustomerMapper;
import io.hypersistence.tsid.TSID;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.exception.DataChangedException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class CustomerRepositoryImpl implements CustomerRepository {

    private final DSLContext dslContext;
    private final CustomerDAO customerDAO;
    private final TSID.Factory tsidFactory;
    private final CustomerMapper customerMapper;

    public CustomerRepositoryImpl(
            DSLContext dslContext,
            CustomerDAO customerDAO,
            TSID.Factory tsidFactory,
            CustomerMapper customerMapper) {
        this.dslContext = dslContext;
        this.customerDAO = customerDAO;
        this.tsidFactory = tsidFactory;
        this.customerMapper = customerMapper;
    }

    @Override
    public Optional<CustomersRecord> findById(Long id) {
        return customerDAO.findById(id);
    }

    @Override
    public Optional<CustomersRecord> findByName(String name) {
        return customerDAO.findByName(name);
    }

    @Override
    public Optional<CustomersRecord> findByEmail(String normalizedEmail) {
        return customerDAO.findByEmail(normalizedEmail);
    }

    @Override
    public List<CustomersRecord> findAll(int offset, int limit, List<OrderField<?>> orderFields) {
        return customerDAO.findAll(offset, limit, orderFields);
    }

    @Override
    public boolean existsByEmail(String email) {
        return customerDAO.existsByEmail(email);
    }

    @Override
    public long count() {
        return customerDAO.count();
    }

    /**
     * Inserts a requestCustomer without an ID or updates one whose ID and version match the stored
     * row. Lowercases a non-null email on the supplied requestCustomer. Inserts also assign it an
     * ID and version zero. Updates treat a null version as zero and increment the stored version,
     * leaving the supplied instance's version unchanged. Database failures propagate to the caller.
     *
     * @return the requestCustomer returned by the database, including its persisted version
     * @throws org.springframework.dao.OptimisticLockingFailureException if an update finds no row
     *     matching the supplied ID and version
     */
    @Override
    @Transactional
    public CustomersRecord saveCustomer(CustomersRecord requestCustomer) {
        normalizeCustomer(requestCustomer);
        boolean isNew = requestCustomer.getId() == null;
        if (isNew) {
            requestCustomer.setId(tsidFactory.generate().toLong());
            if (requestCustomer.getVersion() == null) {
                requestCustomer.setVersion(0);
            }
            CustomersRecord customersRecord = dslContext.newRecord(CUSTOMERS, requestCustomer);
            return dslContext
                    .insertInto(CUSTOMERS)
                    .set(customersRecord)
                    .returningResult()
                    .fetchOneInto(CustomersRecord.class);
        } else {
            CustomersRecord existingRecord =
                    customerDAO
                            .findById(requestCustomer.getId())
                            .orElseThrow(
                                    () ->
                                            new OptimisticLockingFailureException(
                                                    "Customer was updated or deleted by another transaction"));

            int expectedVersion =
                    requestCustomer.getVersion() == null ? 0 : requestCustomer.getVersion();
            if (existingRecord.getVersion() != null
                    && !existingRecord.getVersion().equals(expectedVersion)) {
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
    }

    /**
     * Inserts all supplied customers, assigning missing IDs and setting null versions to zero on
     * the input instances. Existing IDs and versions are retained for insertion, and email
     * addresses are stored as supplied. Database failures propagate to the caller.
     *
     * @param customerList the nonempty list of customers to insert
     * @return the inserted customers returned by the database
     * @throws java.util.NoSuchElementException if the list is empty
     */
    @Override
    @Transactional
    public List<CustomersRecord> saveAll(List<CustomersRecord> customerList) {
        for (var customer : customerList) {
            normalizeCustomer(customer);
            if (customer.getId() == null) {
                customer.setId(tsidFactory.generate().toLong());
            }
            if (customer.getVersion() == null) {
                customer.setVersion(0);
            }
        }
        var insertStepN =
                dslContext
                        .insertInto(CUSTOMERS)
                        .set(dslContext.newRecord(CUSTOMERS, customerList.getFirst()));
        for (var customer : customerList.subList(1, customerList.size())) {
            insertStepN = insertStepN.newRecord().set(dslContext.newRecord(CUSTOMERS, customer));
        }
        return insertStepN.returning().fetch().into(CustomersRecord.class);
    }

    @Override
    @Transactional
    public int deleteById(Long id) {
        return customerDAO.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteAll() {
        dslContext.deleteFrom(CUSTOMERS).execute();
    }

    private void normalizeCustomer(CustomersRecord customer) {
        if (customer.getEmail() != null) {
            customer.setEmail(customer.getEmail().toLowerCase(Locale.ROOT));
        }
    }
}
