/*** Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import com.example.paymentservice.entities.Customer;
import com.example.paymentservice.model.response.CustomerResponse;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<CustomerResponse> findByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Customer> findById(Long customerId);
}
