/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import com.example.catalogservice.entities.OutboxEvent;
import com.example.catalogservice.entities.OutboxEventStatus;
import com.example.catalogservice.entities.OutboxPayload;
import com.example.catalogservice.repositories.OutboxEventRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    public OutboxService(OutboxEventRepository outboxEventRepository, JsonMapper jsonMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Serializes the payload and saves a pending outbox event.
     *
     * @param aggregateType type of aggregate that produced the event
     * @param aggregateId identifier of the aggregate
     * @param eventType type of event to publish
     * @param payload event data to serialize as JSON
     * @return a publisher that saves and emits the new event on subscription
     */
    @Transactional
    public Mono<OutboxEvent> createOutboxEvent(
            String aggregateType, String aggregateId, String eventType, Object payload) {
        String payloadString = jsonMapper.writeValueAsString(payload);
        OutboxEvent event =
                new OutboxEvent()
                        .setId(UUID.randomUUID())
                        .setAggregateType(aggregateType)
                        .setAggregateId(aggregateId)
                        .setEventType(eventType)
                        .setPayload(new OutboxPayload(payloadString))
                        .setStatus(OutboxEventStatus.PENDING)
                        .setNew(true)
                        .setCreatedAt(OffsetDateTime.now())
                        .setRetryCount(0);

        log.info(
                "Saving outbox event: {} for aggregate: {} to database",
                event.getId(),
                aggregateId);
        return outboxEventRepository.save(event);
    }
}
