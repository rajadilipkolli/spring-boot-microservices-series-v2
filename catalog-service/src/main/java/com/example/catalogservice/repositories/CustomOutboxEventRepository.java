/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.repositories;

import com.example.catalogservice.entities.OutboxEvent;
import reactor.core.publisher.Flux;

public interface CustomOutboxEventRepository {

    /**
     * Claims the oldest pending events, skipping rows locked by other transactions. Marks them as
     * processing, records the lock time, and increments their versions.
     *
     * @param limit the maximum number of events to claim; zero claims none
     * @return the claimed events with updated state; database failures are emitted as errors
     */
    Flux<OutboxEvent> claimPendingEvents(int limit);
}
