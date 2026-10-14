/*** Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import com.example.paymentservice.jooq.tables.records.CustomersRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.OrderField;

public interface CustomerRepository {

    Optional<CustomersRecord> findById(Long id);

    Optional<CustomersRecord> findByName(String name);

    Optional<CustomersRecord> findByEmail(String normalizedEmail);

    List<CustomersRecord> findAll(int offset, int limit, List<OrderField<?>> orderFields);

    boolean existsByEmail(String email);

    long count();

    List<CustomersRecord> saveAll(List<CustomersRecord> customerList);

    CustomersRecord saveCustomer(CustomersRecord requestCustomer);

    void deleteAll();

    int deleteById(Long id);
}
