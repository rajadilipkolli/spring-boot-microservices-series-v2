/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.events;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.utils.AppConstants;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private final KafkaTemplate<String, OrderDto> kafkaTemplate;

    public OrderEventPublisher(KafkaTemplate<String, OrderDto> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @ApplicationModuleListener
    public void onOrderCreated(OrderCreatedEvent event) throws Exception {
        OrderDto order = event.order();
        kafkaTemplate
                .send(AppConstants.ORDERS_TOPIC, String.valueOf(order.orderId()), order)
                .get(10, TimeUnit.SECONDS);
    }
}
