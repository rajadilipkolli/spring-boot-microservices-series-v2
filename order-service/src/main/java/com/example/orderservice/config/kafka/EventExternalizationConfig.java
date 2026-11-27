/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.config.kafka;

import static com.example.orderservice.utils.AppConstants.ORDERS_TOPIC;

import com.example.common.dto.OrderDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

@Configuration(proxyBeanMethods = false)
class EventExternalizationConfig {

    /**
     * Routes published {@link OrderDto} domain events to the {@code orders} topic, keyed by the
     * order ID.
     */
    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .selectByType(OrderDto.class)
                .route(
                        OrderDto.class,
                        it ->
                                RoutingTarget.forTarget(ORDERS_TOPIC)
                                        .andKey(String.valueOf(it.orderId())))
                .build();
    }
}
