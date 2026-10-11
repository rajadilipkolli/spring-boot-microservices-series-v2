/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.exception;

public class OrderStoreUnavailableException extends RuntimeException {
    /** Creates an exception with the standard orders-store unavailability message. */
    public OrderStoreUnavailableException() {
        super("Orders store is temporarily unavailable");
    }

    /** Creates an exception with the standard unavailability message and the supplied cause. */
    public OrderStoreUnavailableException(Throwable cause) {
        super("Orders store is temporarily unavailable", cause);
    }
}
