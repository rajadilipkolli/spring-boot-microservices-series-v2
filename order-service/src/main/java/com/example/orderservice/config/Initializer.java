/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import com.example.orderservice.services.OrderService;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.scheduling.cron.Cron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class Initializer implements CommandLineRunner {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final OrderService orderService;

    public Initializer(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public void run(String... args) {
        log.info("Running Initializer.....");
        BackgroundJob.scheduleRecurrently(Cron.minutely(), orderService::retryNewOrders);
        log.info("Completed Scheduling Recurrently BackgroundJob with 2 retries");
    }
}
