/*** Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import static com.example.paymentservice.jooq.tables.Customers.CUSTOMERS;

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.model.response.CustomerResponse;
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
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class CustomerRepositoryImpl implements CustomerRepository {

    private final DSLContext dslContext;

    public CustomerRepositoryImpl(DSLContext dslContext) {
        this.dslContext = dslContext;
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
    @Transactional
    public Customer save(Customer customer) {
        if (customer.getId() == null) {
            CustomersRecord customersRecord = dslContext.newRecord(CUSTOMERS, customer);
            return dslContext
                    .insertInto(CUSTOMERS)
                    .set(customersRecord)
                    .returningResult()
                    .fetchOneInto(Customer.class);
        } else {
            return dslContext
                    .update(CUSTOMERS)
                    .set(CUSTOMERS.AMOUNT_AVAILABLE, customer.getAmountAvailable())
                    .set(CUSTOMERS.AMOUNT_RESERVED, customer.getAmountReserved())
                    .set(CUSTOMERS.ADDRESS, customer.getAddress())
                    .set(CUSTOMERS.NAME, customer.getName())
                    .set(CUSTOMERS.EMAIL, customer.getEmail())
                    .where(CUSTOMERS.ID.eq(customer.getId()))
                    .returningResult()
                    .fetchOneInto(Customer.class);
        }
    }

    @Override
    @Transactional
    public List<Customer> saveAll(List<Customer> customerList) {
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
