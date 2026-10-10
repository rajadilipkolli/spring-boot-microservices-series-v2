/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.jobs;

import java.time.Duration;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

@Component
public class OutboxCleanupJob extends QuartzJobBean {

    private final CompletedEventPublications completedEvents;

    public OutboxCleanupJob(CompletedEventPublications completedEvents) {
        this.completedEvents = completedEvents;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        completedEvents.deletePublicationsOlderThan(Duration.ofHours(24));
    }
}
