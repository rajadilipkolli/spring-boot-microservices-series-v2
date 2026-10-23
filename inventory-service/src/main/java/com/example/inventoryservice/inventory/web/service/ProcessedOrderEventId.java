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

    public ProcessedOrderEventId() {}

    public ProcessedOrderEventId(Long orderId, String eventType) {
        this.orderId = orderId;
        this.eventType = eventType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProcessedOrderEventId that = (ProcessedOrderEventId) o;
        return Objects.equals(orderId, that.orderId) && Objects.equals(eventType, that.eventType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, eventType);
    }
}
