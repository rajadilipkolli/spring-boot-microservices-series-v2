/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import java.util.Objects;

/**
 * Value object representing an idempotency key.
 *
 * @param value the raw key string
 */
public record IdempotencyKey(String value) {

    public IdempotencyKey {
        Objects.requireNonNull(value, "Idempotency key value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Idempotency key value must not be blank");
        }
    }
}
