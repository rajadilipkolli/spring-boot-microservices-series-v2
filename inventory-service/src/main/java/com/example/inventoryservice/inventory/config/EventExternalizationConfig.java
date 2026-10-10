/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.config;

import com.example.inventoryservice.inventory.events.InventoryProcessedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;

@Configuration(proxyBeanMethods = false)
class EventExternalizationConfig {

    /**
     * Selects events annotated for externalization and routes inventory outcomes to {@code
     * stock-orders}, keyed by the order ID as a string.
     */
    @Bean
    EventExternalizationConfiguration eventExternalizationConfiguration() {
        return EventExternalizationConfiguration.externalizing()
                .select(EventExternalizationConfiguration.annotatedAsExternalized())
                .route(
                        InventoryProcessedEvent.class,
                        it ->
                                RoutingTarget.forTarget("stock-orders")
                                        .andKey(String.valueOf(it.order().orderId())))
                .build();
    }
}
