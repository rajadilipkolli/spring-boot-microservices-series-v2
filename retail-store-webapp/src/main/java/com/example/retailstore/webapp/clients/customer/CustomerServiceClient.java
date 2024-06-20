package com.example.retailstore.webapp.clients.customer;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

public interface CustomerServiceClient {

    @PostExchange("/api/customers")
    CustomerResponse getOrCreateCustomer(@RequestBody CustomerRequest customer);
}
