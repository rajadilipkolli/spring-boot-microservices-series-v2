/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import com.example.orderservice.events.OrderCreatedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

@Configuration
public class EventExternalizationConfig {

    @Bean
    public EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .selectByType(OrderCreatedEvent.class)
                .route(
                        OrderCreatedEvent.class,
                        event ->
                                RoutingTarget.forTarget("orders")
                                        .andKey(String.valueOf(event.order().orderId())))
                .mapping(OrderCreatedEvent.class, OrderCreatedEvent::order)
                .build();
    }
}
