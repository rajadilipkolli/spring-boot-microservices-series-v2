/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.events;

import com.example.common.dtos.OrderDto;
import org.springframework.modulith.events.Externalized;

@Externalized
public record OrderCreatedEvent(OrderDto order) {}
