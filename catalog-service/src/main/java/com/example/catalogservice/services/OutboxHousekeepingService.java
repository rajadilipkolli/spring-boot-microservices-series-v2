/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import com.example.catalogservice.entities.OutboxEventStatus;
import com.example.catalogservice.repositories.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;

@Service
public class OutboxHousekeepingService {

    private static final Logger log = LoggerFactory.getLogger(OutboxHousekeepingService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final AtomicLong pendingCount = new AtomicLong(0);
    private final AtomicLong publishedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    public OutboxHousekeepingService(
            OutboxEventRepository outboxEventRepository, MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;

        meterRegistry.gauge("outbox.events.pending", pendingCount);
        meterRegistry.gauge("outbox.events.published", publishedCount);
        meterRegistry.gauge("outbox.events.failed", failedCount);
    }

    @Scheduled(fixedDelay = 10000)
    public void updateMetrics() {
        outboxEventRepository
                .countByStatus(OutboxEventStatus.PENDING)
                .doOnNext(pendingCount::set)
                .then(outboxEventRepository.countByStatus(OutboxEventStatus.PUBLISHED))
                .doOnNext(publishedCount::set)
                .then(outboxEventRepository.countByStatus(OutboxEventStatus.FAILED))
                .doOnNext(failedCount::set)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    @Scheduled(cron = "0 0 1 * * *") // Daily at 1 AM
    public void cleanupPublishedEvents() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(7);
        log.info("Starting cleanup of published events older than {}", threshold);
        outboxEventRepository
                .deleteAllByStatusAndCreatedAtBefore(OutboxEventStatus.PUBLISHED, threshold)
                .doOnSuccess(v -> log.info("Successfully cleaned up old published events"))
                .doOnError(e -> log.error("Error during outbox cleanup", e))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }
}
