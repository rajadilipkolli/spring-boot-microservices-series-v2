/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processed_order_events")
@IdClass(ProcessedOrderEventId.class)
public class ProcessedOrderEvent {

    @Id private Long orderId;

    @Id private String eventType;

    private Instant processedAt;

    public ProcessedOrderEvent() {}

    public ProcessedOrderEvent(Long orderId, String eventType, Instant processedAt) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
