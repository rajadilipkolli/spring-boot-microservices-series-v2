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

    /**
     * Retrieves a customer by name using the {@code retailstore-webapp} OAuth2 registration.
     * Authorization and downstream request failures, including a missing customer, propagate to
     * the caller.
     *
     * @param name customer name to look up
     * @return the customer returned by the payment service
     */
    @ClientRegistrationId("retailstore-webapp")
    @GetExchange("/api/customers/name/{name}")
    CustomerResponse getCustomerByName(@PathVariable String name);

    /**
     * Retrieves a customer by ID using the {@code retailstore-webapp} OAuth2 registration.
     * Authorization and downstream request failures, including a missing customer, propagate to
     * the caller.
     *
     * @param id customer ID to look up
     * @return the customer returned by the payment service
     */
    @ClientRegistrationId("retailstore-webapp")
    @GetExchange("/api/customers/{id}")
    CustomerResponse getCustomerById(@PathVariable Long id);

    /**
     * Retrieves a customer by email using the {@code retailstore-webapp} OAuth2 registration.
     * Authorization and downstream request failures, including a missing customer, propagate to
     * the caller.
     *
     * @param email customer email address to look up
     * @return the customer returned by the payment service
     */
    @ClientRegistrationId("retailstore-webapp")
    @GetExchange("/api/customers/by-email")
    CustomerResponse getCustomerByEmail(@RequestParam("email") String email);
}
