/*** Licensed under MIT License Copyright (c) 2022-2024 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.model.response.CustomerResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CustomerRepository {

    Optional<CustomerResponse> findByName(String name);

    Optional<Customer> findById(Long customerId);

    Optional<Customer> findByEmail(String email);

    Customer save(Customer customer);

    void deleteById(Long id);

    Page<Customer> findAll(Pageable pageable);

    List<Customer> saveAll(List<Customer> customerList);

    void deleteAll();
}
