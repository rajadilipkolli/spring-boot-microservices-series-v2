/*** Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli. ***/
package com.example.paymentservice.exception;

public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(Long customerId) {
        super("Customer with Id '%d' not found".formatted(customerId));
    }

    public CustomerNotFoundException(String customerName) {
        super("Customer with Name '%s' not found".formatted(customerName));
    }

    private CustomerNotFoundException(String email, boolean isEmail) {
        super("Customer with Email '%s' not found".formatted(email));
    }

    public static CustomerNotFoundException forEmail(String email) {
        return new CustomerNotFoundException(email, true);
    }
}
