/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import com.example.orderservice.events.OrderCreatedEvent;
import com.example.orderservice.utils.AppConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

// This configuration is necessary because OrderDto resides in the `com.example.common.dtos`
// package.
// Spring Modulith only externalizes events that belong to an application module (which defaults to
// the package
// of the main SpringBootApplication class and its sub-packages). Since `OrderDto` is located
// outside
// the `com.example.orderservice` module boundaries, the `@Externalized` annotation will not be
// processed
// automatically and Modulith will not record its publication in the outbox table.
// Thus, we wrap it in an `OrderCreatedEvent` inside the module, and explicitly instruct Modulith
// to route this event type to the target topic and map its payload to `OrderDto`.
@Configuration(proxyBeanMethods = false)
class EventExternalizationConfig {

    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .selectByType(OrderCreatedEvent.class)
                .route(
                        OrderCreatedEvent.class,
                        event ->
                                RoutingTarget.forTarget(AppConstants.ORDERS_TOPIC)
                                        .andKey(String.valueOf(event.order().orderId())))
                .mapping(OrderCreatedEvent.class, OrderCreatedEvent::order)
                .build();
    }
}
