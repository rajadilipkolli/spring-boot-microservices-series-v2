/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

    @Test
    void shouldCreateValidIdempotencyKey() {
        IdempotencyKey key = new IdempotencyKey("order-123");
        assertThat(key.value()).isEqualTo("order-123");
        assertThat(key.toString()).contains("order-123");
    }

    @Test
    void shouldThrowExceptionWhenValueIsNull() {
        assertThatThrownBy(() -> new IdempotencyKey(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void shouldThrowExceptionWhenValueIsBlank() {
        assertThatThrownBy(() -> new IdempotencyKey("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }
}
