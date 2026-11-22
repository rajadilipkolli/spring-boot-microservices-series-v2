/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

/**
 * Exception thrown when a concurrent or in-flight execution conflict occurs for an idempotency key.
 */
public class IdempotencyConflictException extends RuntimeException {

    private final String key;

    public IdempotencyConflictException(String key) {
        super(
                "Operation with idempotency key '%s' is currently in-flight or conflicted"
                        .formatted(key));
        this.key = key;
    }

    public IdempotencyConflictException(String key, String message) {
        super(message);
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
