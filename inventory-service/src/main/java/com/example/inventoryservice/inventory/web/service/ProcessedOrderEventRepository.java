/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProcessedOrderEventRepository
        extends JpaRepository<ProcessedOrderEvent, ProcessedOrderEventId> {

    boolean existsByOrderIdAndEventType(Long orderId, String eventType);
}
