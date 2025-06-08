package com.example.retailstore.webapp.web.controller;

import com.example.retailstore.webapp.clients.customer.CustomerRequest;
import com.example.retailstore.webapp.clients.customer.CustomerResponse;
import com.example.retailstore.webapp.clients.customer.CustomerServiceClient;
import com.example.retailstore.webapp.services.KeycloakRegistrationService;
import com.example.retailstore.webapp.web.model.request.RegistrationRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
@Validated
public class RegistrationController {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationController.class);

    private final KeycloakRegistrationService registrationService;
    private final CustomerServiceClient customerServiceClient;

    public RegistrationController(
            KeycloakRegistrationService registrationService, CustomerServiceClient customerServiceClient) {
        this.registrationService = registrationService;
        this.customerServiceClient = customerServiceClient;
    }

    @GetMapping("/registration") // Serves the registration page
    public String showRegistrationPage() {
        return "registration"; // Returns the name of the Thymeleaf template (registration.html)
    }

    @PostMapping("/api/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegistrationRequest request) {
        logger.info("Received registration request for user: {}", request.username());
        registrationService.registerUser(request);
        CustomerRequest customerRequest = new CustomerRequest(
                request.username(), request.email(), String.valueOf(request.phone()), request.address(), 10_000);
        CustomerResponse customerResponse = customerServiceClient.getOrCreateCustomer(customerRequest);

        logger.info("User {} registered successfully with id :{}", request.username(), customerResponse.customerId());
        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }
}
