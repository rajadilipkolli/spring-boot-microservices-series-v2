/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services.provider;

public class ProviderTimeoutException extends RuntimeException {

    public ProviderTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
