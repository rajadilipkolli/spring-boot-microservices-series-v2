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

    private String resultStatus;

    /** Creates an empty processing record for JPA to populate. */
    public ProcessedOrderEvent() {}

    /**
     * Creates a processing record without persisting it.
     *
     * @param orderId unique order identifier
     * @param eventType duplicate-tracking category: {@code RESERVE} or {@code CONFIRM_<status>}
     * @param processedAt instant at which the service recorded processing
     */
    public ProcessedOrderEvent(Long orderId, String eventType, Instant processedAt) {
        this(orderId, eventType, processedAt, null);
    }

    /**
     * Creates a processing record with a reservation outcome status.
     *
     * @param orderId unique order identifier
     * @param eventType duplicate-tracking category: {@code RESERVE} or {@code CONFIRM_<status>}
     * @param processedAt instant at which the service recorded processing
     * @param resultStatus outcome of the reservation (e.g. {@code ACCEPT} or {@code REJECT})
     */
    public ProcessedOrderEvent(
            Long orderId, String eventType, Instant processedAt, String resultStatus) {
        this.orderId = orderId;
        this.eventType = eventType;
        this.processedAt = processedAt;
        this.resultStatus = resultStatus;
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

    /** Returns the recorded result status, or null if not yet determined or applicable. */
    public String getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }
}
