/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.exception;

public class OperationConflictException extends RuntimeException {

    public OperationConflictException(String message) {
        super(message);
    }
}
