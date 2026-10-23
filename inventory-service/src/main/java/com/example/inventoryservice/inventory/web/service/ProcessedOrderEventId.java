/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import java.io.Serializable;
import java.util.Objects;

public class ProcessedOrderEventId implements Serializable {
    private Long orderId;
    private String eventType;

    /** Creates an empty composite key for JPA to populate. */
    public ProcessedOrderEventId() {}

    /**
     * Creates a composite key identifying one order's processing category.
     *
     * @param eventType duplicate-tracking category: {@code RESERVE} or {@code CONFIRM_<status>}
     */
    public ProcessedOrderEventId(Long orderId, String eventType) {
        this.orderId = orderId;
        this.eventType = eventType;
    }

    /** Returns whether the other object has the same runtime class, order ID, and event type. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessedOrderEventId that = (ProcessedOrderEventId) o;
        return Objects.equals(orderId, that.orderId) && Objects.equals(eventType, that.eventType);
    }

    /** Computes a hash from the order ID and event type, consistent with {@link #equals(Object)}. */
    @Override
    public int hashCode() {
        return Objects.hash(orderId, eventType);
    }
}
