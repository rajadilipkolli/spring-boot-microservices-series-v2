/*** Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli. ***/
package com.example.paymentservice.services.listener;

import com.example.common.dtos.OrderDto;
import com.example.paymentservice.services.OrderManageService;
import com.example.paymentservice.utils.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@EnableKafka
public class KafkaListenerConfig {

    private final OrderManageService orderManageService;

    @KafkaListener(id = "orders", topics = AppConstants.ORDERS_TOPIC, groupId = "payment")
    public void onEvent(OrderDto orderDto) {
        log.info(
                "Received Order in payment service: {} from topic: {}",
                orderDto,
                AppConstants.ORDERS_TOPIC);
        if ("NEW".equals(orderDto.getStatus())) {
            orderManageService.reserve(orderDto);
        } else {
            orderManageService.confirm(orderDto);
        }
    }
}
