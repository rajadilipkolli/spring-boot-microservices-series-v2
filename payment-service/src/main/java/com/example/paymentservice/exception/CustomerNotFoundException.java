/* Licensed under Apache-2.0 2023 */
package com.example.paymentservice.exception;

import org.springframework.http.HttpStatus;

public class CustomerNotFoundException extends RuntimeException {

    private final HttpStatus httpStatus = HttpStatus.NOT_FOUND;

    public CustomerNotFoundException(Long customerId) {
        super("Customer with Id " + customerId + "Not Found");
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
