/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Infrastructure scheduler executing background retries for stuck or unprocessed orders. */
@Component
public class OrderRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderRetryScheduler.class);

    private final OrderRetryService orderRetryService;

    public OrderRetryScheduler(OrderRetryService orderRetryService) {
        this.orderRetryService = orderRetryService;
    }

    /**
     * Periodically triggers retry of orders stuck in NEW status. Configured as a recurring JobRunr
     * job to execute minutely.
     */
    @Job(name = "reProcessNewOrders", retries = 2)
    @Recurring(id = "retry-new-orders", cron = "*/1 * * * *")
    public void retryNewOrders() {
        log.debug("Triggering periodic retry for NEW orders");
        orderRetryService.retryNewOrders();
    }
}
