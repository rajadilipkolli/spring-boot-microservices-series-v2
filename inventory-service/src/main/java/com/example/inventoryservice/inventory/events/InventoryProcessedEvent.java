/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.events;

import com.example.inventoryservice.inventory.domain.models.OrderDto;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.modulith.events.Externalized;

/**
 * Externalizable Spring Modulith event representing the outcome of inventory processing for an
 * order, routed to the {@code stock-orders} Kafka topic.
 *
 * <p><b>Why {@code @JsonValue} is used:</b> Annotating {@code @JsonValue} on {@code order}
 * instructs Jackson to serialize the underlying {@link OrderDto} directly as the root JSON payload
 * instead of wrapping it inside a nested JSON envelope like {@code {"order": { ... }}}.<br>
 * <i>What happens if not added:</i> Downstream consumers (such as {@code order-service}'s Kafka
 * Streams topology) expect a flat {@link OrderDto} JSON object on {@code stock-orders}. Without
 * {@code @JsonValue}, the output JSON would be wrapped in {@code {"order": { ... }}}, causing
 * deserialization failures or unpopulated fields on consumers expecting the raw order structure.
 *
 * <p><b>Why {@code @JsonCreator} is used:</b> Annotating {@code @JsonCreator} on the
 * single-argument compact constructor tells Jackson to instantiate {@link InventoryProcessedEvent}
 * by delegating directly to the unwrapped {@link OrderDto} payload during deserialization (e.g. in
 * test suites, event reprocessing, or outbox recovery).<br>
 * <i>What happens if not added:</i> When {@code @JsonValue} unwraps serialization to a single
 * delegate, Jackson requires a matching {@code @JsonCreator} to map that unwrapped JSON payload
 * back into the wrapper record. Without it, Jackson throws deserialization exceptions (such as
 * {@code InvalidDefinitionException} or {@code MismatchedInputException}) when attempting to read
 * the event.
 */
@Externalized("stock-orders")
public record InventoryProcessedEvent(@JsonValue OrderDto order) {
    /**
     * Compact constructor allowing Jackson to deserialize directly from the unwrapped {@link
     * OrderDto}.
     */
    @JsonCreator
    public InventoryProcessedEvent(OrderDto order) {
        this.order = order;
    }
}
