/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.exception;

public class OperationConflictException extends RuntimeException {

    /** Creates a conflict exception with the supplied description of the incompatible operation. */
    public OperationConflictException(String message) {
        super(message);
    }
}
