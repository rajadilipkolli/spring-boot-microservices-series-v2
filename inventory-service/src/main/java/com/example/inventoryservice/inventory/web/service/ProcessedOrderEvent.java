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

    /** Creates an empty processing record for JPA to populate. */
    public ProcessedOrderEvent() {}

    /**
     * Creates a processing record without persisting it.
     *
     * @param eventType duplicate-tracking category: {@code RESERVE} or {@code CONFIRM_<status>}
     * @param processedAt instant at which the service recorded processing
     */
    public ProcessedOrderEvent(Long orderId, String eventType, Instant processedAt) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.processedAt = processedAt;
    }

    /** Returns the order identifier used with the event type to detect prior processing. */
    public Long getOrderId() {
        return orderId;
    }

    /**
     * Returns the duplicate-tracking category, such as {@code RESERVE} or {@code CONFIRM_<status>}.
     */
    public String getEventType() {
        return eventType;
    }

    /** Returns the recorded processing instant. */
    public Instant getProcessedAt() {
        return processedAt;
    }
}
