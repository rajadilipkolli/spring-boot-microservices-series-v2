/*** Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli. ***/
package com.example.paymentservice.services.listener;

import com.example.common.dtos.OrderDto;
import com.example.paymentservice.services.PaymentOrderManageService;
import com.example.paymentservice.utils.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
@EnableKafka
public class KafkaListenerConfig {

    private final PaymentOrderManageService paymentOrderManageService;

    @KafkaListener(
            id = "orders",
            topics = AppConstants.ORDERS_TOPIC,
            groupId = "payment",
            concurrency = "3")
    @Transactional("kafkaTransactionManager")
    public void onEvent(OrderDto orderDto) {
        log.info(
                "Received Order in payment service : {} from topic: {} with source :{}",
                orderDto,
                AppConstants.ORDERS_TOPIC,
                orderDto.getSource());
        if ("NEW".equals(orderDto.getStatus())) {
            paymentOrderManageService.reserve(orderDto);
        } else {
            paymentOrderManageService.confirm(orderDto);
        }
    }
}
