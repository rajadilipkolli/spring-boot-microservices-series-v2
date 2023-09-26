/*** Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli. ***/
package com.example.paymentservice.repositories;

import com.example.paymentservice.model.response.CustomerResponse;
import java.util.Optional;

public interface CustomerRepository {

    Optional<CustomerResponse> findByName(String name);
}
