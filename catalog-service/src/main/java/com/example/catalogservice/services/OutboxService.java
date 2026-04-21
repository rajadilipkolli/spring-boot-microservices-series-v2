/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import com.example.catalogservice.entities.OutboxEvent;
import com.example.catalogservice.entities.OutboxEventStatus;
import com.example.catalogservice.entities.Product;
import com.example.catalogservice.mapper.ProductMapper;
import com.example.catalogservice.repositories.OutboxEventRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;
    private final ProductMapper productMapper;

    public OutboxService(
            OutboxEventRepository outboxEventRepository,
            JsonMapper jsonMapper,
            ProductMapper productMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.jsonMapper = jsonMapper;
        this.productMapper = productMapper;
    }

    @Transactional
    public Mono<OutboxEvent> createOutboxEvent(
            String aggregateType, String aggregateId, String eventType, Object payload) {
        try {
            Object finalPayload = payload;
            if (payload instanceof Product product) {
                finalPayload = productMapper.toProductDto(product);
            }
            JsonNode payloadNode = jsonMapper.valueToTree(finalPayload);
            OutboxEvent event =
                    new OutboxEvent()
                            .setId(UUID.randomUUID())
                            .setAggregateType(aggregateType)
                            .setAggregateId(aggregateId)
                            .setEventType(eventType)
                            .setPayload(payloadNode)
                            .setStatus(OutboxEventStatus.PENDING)
                            .setNew(true)
                            .setCreatedAt(OffsetDateTime.now())
                            .setRetryCount(0);

            log.info(
                    "Saving outbox event: {} for aggregate: {} to database",
                    event.getId(),
                    aggregateId);
            return outboxEventRepository.save(event);
        } catch (JacksonException e) {
            log.error("Error serializing outbox event payload", e);
            return Mono.error(e);
        }
    }
}
