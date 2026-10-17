/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.exception;

public class ReservationNotFoundException extends RuntimeException {

    /** Creates an exception identifying the order without a completed reservation. */
    public ReservationNotFoundException(Long orderId) {
        super("No completed reservation found for orderId: " + orderId);
    }
}
