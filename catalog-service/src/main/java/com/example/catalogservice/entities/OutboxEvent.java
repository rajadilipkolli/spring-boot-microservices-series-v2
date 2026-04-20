/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.entities;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;
import tools.jackson.databind.JsonNode;

@Table("outbox_events")
public class OutboxEvent implements Persistable<UUID> {

    @Id private UUID id;

    @Transient private boolean isNew = false;

    private String aggregateType;

    private String aggregateId;

    private String eventType;

    private JsonNode payload;

    private OutboxEventStatus status;

    private OffsetDateTime createdAt;

    private OffsetDateTime processedAt;

    private int retryCount;

    private String errorMessage;

    public OutboxEvent() {}

    public UUID getId() {
        return id;
    }

    public OutboxEvent setId(UUID id) {
        this.id = id;
        return this;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public OutboxEvent setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
        return this;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public OutboxEvent setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
        return this;
    }

    public String getEventType() {
        return eventType;
    }

    public OutboxEvent setEventType(String eventType) {
        this.eventType = eventType;
        return this;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public OutboxEvent setPayload(JsonNode payload) {
        this.payload = payload;
        return this;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public OutboxEvent setStatus(OutboxEventStatus status) {
        this.status = status;
        return this;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OutboxEvent setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public OutboxEvent setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
        return this;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public OutboxEvent setRetryCount(int retryCount) {
        this.retryCount = retryCount;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public OutboxEvent setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    @Override
    @Transient
    public boolean isNew() {
        return this.isNew || this.id == null;
    }

    public OutboxEvent setNew(boolean isNew) {
        this.isNew = isNew;
        return this;
    }
}
