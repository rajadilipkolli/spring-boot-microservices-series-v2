package com.example.retailstore.webapp.services;

import com.example.retailstore.webapp.clients.customer.CustomerRequest;
import com.example.retailstore.webapp.clients.customer.CustomerResponse;
import com.example.retailstore.webapp.clients.customer.CustomerServiceClient;
import com.example.retailstore.webapp.model.request.RegistrationRequest;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
public class UserRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(UserRegistrationService.class);

    private final IdentityProviderPort identityProviderPort;
    private final CustomerServiceClient customerServiceClient;

    public UserRegistrationService(
            IdentityProviderPort identityProviderPort, CustomerServiceClient customerServiceClient) {
        this.identityProviderPort = identityProviderPort;
        this.customerServiceClient = customerServiceClient;
    }

    /**
     * Registers a Keycloak user, then gets or creates the payment customer using the supplied
     * address components and an initial available balance of 10,000. Keycloak and customer-service
     * failures propagate, including duplicate-user errors. A confirmed customer validation failure
     * (HTTP 400) or duplicate rejection (HTTP 409) triggers an attempt to delete the Keycloak user
     * by username; cleanup exceptions are suppressed and the original customer error is rethrown.
     * Other customer-service failures preserve the Keycloak user because customer creation may
     * have completed.
     *
     * @param request validated user and customer registration details
     * @return the customer response after both service calls complete
     */
    public CustomerResponse registerUserAndCustomer(RegistrationRequest request) {
        log.info("Received registration request for user: {}", request.username());
        identityProviderPort.registerUser(request);
        try {
            CustomerRequest customerRequest = new CustomerRequest(
                    request.username(),
                    request.email(),
                    String.valueOf(request.phone()),
                    request.addressLine1(),
                    request.addressLine2(),
                    request.city(),
                    request.state(),
                    request.zipCode(),
                    request.country(),
                    BigDecimal.valueOf(10_000));
            CustomerResponse customerResponse = customerServiceClient.getOrCreateCustomer(customerRequest);

            log.info("User {} registered successfully with id: {}", request.username(), customerResponse.customerId());
            return customerResponse;
        } catch (HttpClientErrorException ex) {
            // These statuses are explicit rejections in the customer creation API contract.
            if (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 409) {
                log.error("Customer creation rejected for user {}; compensating Keycloak user", request.username(), ex);
                identityProviderPort.deleteUserByUsername(request.username());
            }
            throw ex;
        }
    }
}
