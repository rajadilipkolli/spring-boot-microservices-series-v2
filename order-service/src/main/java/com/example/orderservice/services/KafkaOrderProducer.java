/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.config.logging.Loggable;
import com.example.orderservice.utils.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Loggable
public class KafkaOrderProducer {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final KafkaTemplate<Long, OrderDto> kafkaTemplate;

    public KafkaOrderProducer(KafkaTemplate<Long, OrderDto> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

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
