/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.exception;

public class OrderStoreUnavailableException extends RuntimeException {
    public OrderStoreUnavailableException() {
        super("Orders store is temporarily unavailable");
    }

    public OrderStoreUnavailableException(Throwable cause) {
        super("Orders store is temporarily unavailable", cause);
    }
}
