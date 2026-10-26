package com.example.retailstore.webapp.clients.customer;

import org.springframework.security.oauth2.client.annotation.ClientRegistrationId;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/payment-service")
public interface CustomerServiceClient {

    @PostExchange("/api/customers")
    CustomerResponse getOrCreateCustomer(@RequestBody CustomerRequest customer);

    @ClientRegistrationId("retailstore-webapp")
    @GetExchange("/api/customers/name/{name}")
    CustomerResponse getCustomerByName(@PathVariable String name);

    @ClientRegistrationId("retailstore-webapp")
    @GetExchange("/api/customers/{id}")
    CustomerResponse getCustomerById(@PathVariable Long id);

    @ClientRegistrationId("retailstore-webapp")
    @GetExchange("/api/customers/by-email")
    CustomerResponse getCustomerByEmail(@RequestParam("email") String email);
}
