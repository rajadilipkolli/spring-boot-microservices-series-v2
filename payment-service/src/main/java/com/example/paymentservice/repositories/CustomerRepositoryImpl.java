/*** Licensed under MIT License Copyright (c) 2023 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import static com.example.paymentservice.jooq.tables.Customers.CUSTOMERS;

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import com.example.paymentservice.model.response.CustomerResponse;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CustomerRepositoryImpl implements CustomerRepository {

    private final DSLContext dsl;

    @Override
    public Optional<CustomerResponse> findByName(String name) {
        return dsl.select()
                .from(CUSTOMERS)
                .where(CUSTOMERS.NAME.eq(name))
                .fetchOptionalInto(CustomerResponse.class);
    }

    @Override
    public Optional<Customer> findById(Long customerId) {
        return dsl.select()
                .from(CUSTOMERS)
                .where(CUSTOMERS.ID.eq(customerId))
                .fetchOptionalInto(Customer.class);
    }

    @Override
    public Customer save(Customer customer) {
        CustomersRecord customersRecord = dsl.newRecord(CUSTOMERS, customer);
        return Objects.requireNonNull(
                        dsl.insertInto(CUSTOMERS).set(customersRecord).returningResult().fetchOne())
                .into(Customer.class);
    }

    @Override
    public void deleteById(Long id) {
        dsl.deleteFrom(CUSTOMERS).where(CUSTOMERS.ID.eq(id)).execute();
    }

    @Override
    public Page<Customer> findAll(Pageable pageable) {
        return new PageImpl<>(
                dsl.select()
                        .from(CUSTOMERS)
                        .orderBy(CUSTOMERS.ID.asc())
                        .limit(pageable.getPageSize())
                        .offset(pageable.getOffset())
                        .fetch()
                        .into(Customer.class));
    }

    @Override
    public List<Customer> saveAll(List<Customer> customerList) {
        InsertSetMoreStep<CustomersRecord> insertStepN =
                dsl.insertInto(CUSTOMERS).set(dsl.newRecord(CUSTOMERS, customerList.get(0)));
        for (var record : customerList.subList(1, customerList.size())) {
            insertStepN = insertStepN.newRecord().set(dsl.newRecord(CUSTOMERS, record));
        }
        return insertStepN.returning().fetch().into(Customer.class);
    }

    @Override
    public void deleteAll() {
        dsl.deleteFrom(CUSTOMERS).execute();
    }
}
