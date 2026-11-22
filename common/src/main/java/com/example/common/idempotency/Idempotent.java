/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

/**
 * Annotation to mark a method as idempotent.
 *
 * <p>Duplicate requests with the same evaluated key will either return the previously cached result
 * or throw an {@link IdempotencyConflictException} if an execution is currently in flight.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * SpEL expression to evaluate the idempotency key against method arguments. E.g., {@code
     * "#orderId"} or {@code "#request.idempotencyKey"}. If blank, defaults to a composite hash of
     * method name and arguments.
     */
    String key() default "";

    /** Duration value for the TTL (time-to-live) of the lock and cached result. */
    long ttl() default 60;

    /** Time unit for the {@link #ttl()}. Defaults to {@link ChronoUnit#SECONDS}. */
    ChronoUnit ttlUnit() default ChronoUnit.SECONDS;
}
