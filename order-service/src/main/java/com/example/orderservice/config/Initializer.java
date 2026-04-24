/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import com.example.orderservice.services.OrderService;
import com.example.orderservice.services.OutboxRelayService;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.scheduling.cron.Cron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
class Initializer implements CommandLineRunner {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final OrderService orderService;
    private final OutboxRelayService outboxRelayService;

    public Initializer(OrderService orderService, OutboxRelayService outboxRelayService) {
        this.orderService = orderService;
        this.outboxRelayService = outboxRelayService;
    }

    @Override
    public void run(String... args) {
        log.info("Running Initializer.....");
        BackgroundJob.scheduleRecurrently(
                "retry-new-orders", Cron.minutely(), orderService::retryNewOrders);
        BackgroundJob.scheduleRecurrently(
                "outbox-relay", Cron.minutely(), outboxRelayService::processIncompletePublications);
        log.info("Completed scheduling recurring jobs: retryNewOrders, outbox-relay");
    }
}
