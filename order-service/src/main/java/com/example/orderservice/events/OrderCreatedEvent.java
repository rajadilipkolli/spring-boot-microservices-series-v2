/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.events;

import com.example.common.dtos.OrderDto;

public record OrderCreatedEvent(OrderDto order) {}
