/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.utils.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaOrderProducer {

    private final KafkaTemplate<Long, OrderDto> kafkaTemplate;

    @Async
    public void sendOrder(OrderDto persistedOrderDto) {
        kafkaTemplate
                .send(AppConstants.ORDERS_TOPIC, persistedOrderDto.getOrderId(), persistedOrderDto)
                .whenComplete(
                        (result, ex) -> {
                            if (ex == null) {
                                log.info(
                                        "Sent message=[ {} ] from order service with offset=[{}] to topic {}",
                                        persistedOrderDto,
                                        result.getRecordMetadata().offset(),
                                        AppConstants.ORDERS_TOPIC);
                            } else {
                                log.warn(
                                        "Unable to send message=[{}] from order service to {} due to : {}",
                                        persistedOrderDto,
                                        AppConstants.ORDERS_TOPIC,
                                        ex.getMessage());
                            }
                        });
    }
}
