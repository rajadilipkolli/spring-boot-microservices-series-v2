/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import java.time.Duration;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.stereotype.Service;

@Service
public class OutboxRelayService {

    private final IncompleteEventPublications incompleteEvents;

    public OutboxRelayService(IncompleteEventPublications incompleteEvents) {
        this.incompleteEvents = incompleteEvents;
    }

    @Job(name = "outboxRelay", retries = 2)
    public void processIncompletePublications() {
        incompleteEvents.resubmitIncompletePublicationsOlderThan(Duration.ofSeconds(30));
    }
}
