/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_kafka_messages")
public class ProcessedKafkaMessage {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "consumer_group", nullable = false)
    private String consumerGroup;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    public ProcessedKafkaMessage() {}

    public UUID getId() {
        return id;
    }

    public ProcessedKafkaMessage setId(UUID id) {
        this.id = id;
        return this;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public ProcessedKafkaMessage setMessageKey(String messageKey) {
        this.messageKey = messageKey;
        return this;
    }

    public String getTopic() {
        return topic;
    }

    public ProcessedKafkaMessage setTopic(String topic) {
        this.topic = topic;
        return this;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public ProcessedKafkaMessage setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
        return this;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public ProcessedKafkaMessage setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
        return this;
    }
}
