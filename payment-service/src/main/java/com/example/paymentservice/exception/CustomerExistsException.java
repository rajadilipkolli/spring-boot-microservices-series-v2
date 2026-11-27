/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.exception;

public class CustomerExistsException extends RuntimeException {
    /**
     * Creates a duplicate-customer error with the supplied description.
     *
     * @param message the description of the customer conflict
     */
    public CustomerExistsException(String message) {
        super(message);
    }

    /**
     * Creates a duplicate-customer error with the supplied description and cause.
     *
     * @param message the description of the customer conflict
     * @param cause the underlying cause
     */
    public CustomerExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
