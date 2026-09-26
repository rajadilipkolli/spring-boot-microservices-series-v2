/*** Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import static com.example.paymentservice.jooq.tables.Customers.CUSTOMERS;

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.model.response.CustomerResponse;
import io.hypersistence.tsid.TSID;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.SortField;
import org.jooq.TableField;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
public class CustomerRepositoryImpl implements CustomerRepository {

    private final DSLContext dslContext;
    private final TSID.Factory tsidFactory;

    public CustomerRepositoryImpl(DSLContext dslContext, TSID.Factory tsidFactory) {
        this.dslContext = dslContext;
        this.tsidFactory = tsidFactory;
    }

    @Override
    public Page<Customer> findAll(Pageable pageable) {
        return new PageImpl<>(
                dslContext
                        .select()
                        .from(CUSTOMERS)
                        .orderBy(getSortFields(pageable.getSort()))
                        .limit(pageable.getPageSize())
                        .offset(pageable.getOffset())
                        .fetchInto(Customer.class),
                pageable,
                dslContext.fetchCount(CUSTOMERS));
    }

    @Override
    public Optional<CustomerResponse> findByName(String name) {
        return dslContext
                .select(
                        CUSTOMERS.ID,
                        CUSTOMERS.NAME,
                        CUSTOMERS.EMAIL,
                        CUSTOMERS.PHONE,
                        CUSTOMERS.ADDRESS,
                        CUSTOMERS.AMOUNT_AVAILABLE)
                .from(CUSTOMERS)
                .where(CUSTOMERS.NAME.eq(name))
                .fetchOptionalInto(CustomerResponse.class);
    }

    @Override
    public Optional<Customer> findById(Long customerId) {
        return dslContext
                .fetchOptional(CUSTOMERS, CUSTOMERS.ID.eq(customerId))
                .map(r -> r.into(Customer.class));
    }

    @Override
    public Optional<Customer> findByEmail(String customerEmail) {
        String normalizedEmail =
                customerEmail == null ? null : customerEmail.toLowerCase(Locale.ROOT);
        return dslContext
                .fetchOptional(CUSTOMERS, CUSTOMERS.EMAIL.eq(normalizedEmail))
                .map(r -> r.into(Customer.class));
    }

    /**
     * Inserts a customer without an ID or updates one whose ID and version match the stored row.
     * Lowercases a non-null email on the supplied customer. Inserts also assign it an ID and
     * version zero. Updates treat a null version as zero and increment the stored version, leaving
     * the supplied instance's version unchanged. Database failures propagate to the caller.
     *
     * @return the customer returned by the database, including its persisted version
     * @throws org.springframework.dao.OptimisticLockingFailureException if an update finds no row
     *     matching the supplied ID and version
     */
    @Override
    @Transactional
    public Customer save(Customer customer) {
        if (customer.getEmail() != null) {
            customer.setEmail(customer.getEmail().toLowerCase(Locale.ROOT));
        }
        boolean isNew = customer.getId() == null;
        if (isNew) {
            customer.setId(tsidFactory.generate().toLong());
            customer.setVersion(0);
            CustomersRecord customersRecord = dslContext.newRecord(CUSTOMERS, customer);
            return dslContext
                    .insertInto(CUSTOMERS)
                    .set(customersRecord)
                    .returningResult()
                    .fetchOneInto(Customer.class);
        } else {
            Integer currentVersion = customer.getVersion() == null ? 0 : customer.getVersion();
            Customer updatedCustomer =
                    dslContext
                            .update(CUSTOMERS)
                            .set(CUSTOMERS.AMOUNT_AVAILABLE, customer.getAmountAvailable())
                            .set(CUSTOMERS.AMOUNT_RESERVED, customer.getAmountReserved())
                            .set(CUSTOMERS.ADDRESS, customer.getAddress())
                            .set(CUSTOMERS.NAME, customer.getName())
                            .set(CUSTOMERS.EMAIL, customer.getEmail())
                            .set(CUSTOMERS.PHONE, customer.getPhone())
                            .set(CUSTOMERS.VERSION, currentVersion + 1)
                            .where(CUSTOMERS.ID.eq(customer.getId()))
                            .and(CUSTOMERS.VERSION.eq(currentVersion))
                            .returningResult()
                            .fetchOneInto(Customer.class);
            if (updatedCustomer == null) {
                throw new org.springframework.dao.OptimisticLockingFailureException(
                        "Customer was updated or deleted by another transaction");
            }
            return updatedCustomer;
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
    public List<Customer> saveAll(List<Customer> customerList) {
        for (var customer : customerList) {
            if (customer.getId() == null) {
                customer.setId(tsidFactory.generate().toLong());
            }
            if (customer.getVersion() == null) {
                customer.setVersion(0);
            }
        }
        InsertSetMoreStep<CustomersRecord> insertStepN =
                dslContext
                        .insertInto(CUSTOMERS)
                        .set(dslContext.newRecord(CUSTOMERS, customerList.getFirst()));
        for (var customer : customerList.subList(1, customerList.size())) {
            insertStepN = insertStepN.newRecord().set(dslContext.newRecord(CUSTOMERS, customer));
        }
        return insertStepN.returning().fetch().into(Customer.class);
    }

    @Override
    @Transactional
    public void deleteAll() {
        dslContext.deleteFrom(CUSTOMERS).execute();
    }

    @Override
    public int count() {
        return dslContext.fetchCount(CUSTOMERS);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        dslContext.deleteFrom(CUSTOMERS).where(CUSTOMERS.ID.eq(id)).execute();
    }

    private List<SortField<?>> getSortFields(Sort sortSpecification) {
        List<SortField<?>> querySortFields = new ArrayList<>();

        if (sortSpecification == null) {
            return querySortFields;
        }

        for (Sort.Order specifiedField : sortSpecification) {
            String sortFieldName = specifiedField.getProperty();
            Sort.Direction sortDirection = specifiedField.getDirection();

            TableField<CustomersRecord, Object> tableField = getTableField(sortFieldName);
            SortField<?> querySortField = convertTableFieldToSortField(tableField, sortDirection);
            querySortFields.add(querySortField);
        }

        return querySortFields;
    }

    private TableField<CustomersRecord, Object> getTableField(String sortFieldName) {
        TableField<CustomersRecord, Object> sortField;
        try {
            Field tableField =
                    CUSTOMERS.getClass().getField(sortFieldName.toUpperCase(Locale.ROOT));
            sortField = (TableField<CustomersRecord, Object>) tableField.get(CUSTOMERS);
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            String errorMessage = "Could not find table field: %s".formatted(sortFieldName);
            throw new InvalidDataAccessApiUsageException(errorMessage, ex);
        }
        return sortField;
    }

    private SortField<?> convertTableFieldToSortField(
            TableField<CustomersRecord, Object> tableField, Sort.Direction sortDirection) {
        if (sortDirection == Sort.Direction.ASC) {
            return tableField.asc();
        } else {
            return tableField.desc();
        }
    }
}
