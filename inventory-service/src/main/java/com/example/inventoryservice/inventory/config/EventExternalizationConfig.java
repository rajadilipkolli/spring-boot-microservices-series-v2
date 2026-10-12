/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.config;

import com.example.inventoryservice.inventory.events.InventoryProcessedEvent;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.ByteArrayJacksonJsonMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.modulith.events.EventExternalizationConfiguration;
import org.springframework.modulith.events.RoutingTarget;
import tools.jackson.databind.json.JsonMapper;

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

    /**
     * Configures the message converter for event externalization without type headers, preventing
     * internal class names from leaking across service boundaries.
     */
    @Bean
    ByteArrayJacksonJsonMessageConverter byteArrayJacksonJsonMessageConverter(
            ObjectProvider<JsonMapper> jsonMapper) {
        return new ByteArrayJacksonJsonMessageConverter(jsonMapper.getIfUnique(JsonMapper::new)) {
            @Override
            protected Headers initialRecordHeaders(Message<?> message) {
                return new RecordHeaders();
            }
        };
    }
}
