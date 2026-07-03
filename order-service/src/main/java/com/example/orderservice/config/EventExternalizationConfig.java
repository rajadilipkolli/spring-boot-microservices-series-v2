/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import com.example.common.dtos.OrderDto;
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
// automatically. We must explicitly instruct Modulith to route this event type to the target topic.
@Configuration(proxyBeanMethods = false)
class EventExternalizationConfig {

    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .selectByType(OrderDto.class)
                .route(
                        OrderDto.class,
                        event ->
                                RoutingTarget.forTarget("orders")
                                        .andKey(String.valueOf(event.orderId())))
                .mapping(OrderDto.class, event -> event)
                .build();
    }
}
