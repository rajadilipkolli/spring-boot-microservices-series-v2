/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config;

import com.example.inventoryservice.jobs.OutboxCleanupJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Bean
    public JobDetail outboxCleanupJobDetail() {
        return JobBuilder.newJob(OutboxCleanupJob.class)
                .withIdentity("outboxCleanupJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger outboxCleanupJobTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(outboxCleanupJobDetail())
                .withIdentity("outboxCleanupTrigger")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 * * * ?"))
                .build();
    }
}
