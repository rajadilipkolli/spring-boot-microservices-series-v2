/***
<p>
    Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config;

import com.example.common.dtos.OrderDto;
import com.example.common.dtos.ProductDto;
import com.example.inventoryservice.services.InventoryOrderManageService;
import com.example.inventoryservice.services.ProductManageService;
import com.example.inventoryservice.utils.AppConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;

@EnableKafka
@Slf4j
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class KafkaListenerConfig {

    private final InventoryOrderManageService orderManageService;
    private final ProductManageService productManageService;

    @KafkaListener(
            id = "orders",
            topics = AppConstants.ORDERS_TOPIC,
            groupId = "stock",
            concurrency = "3")
    @Transactional("kafkaTransactionManager")
    public void onEvent(OrderDto orderDto) {
        log.info("Received Order: {}", orderDto);
        if ("NEW".equals(orderDto.getStatus())) {
            orderManageService.reserve(orderDto);
        } else {
            orderManageService.confirm(orderDto);
        }
    }

    @KafkaListener(id = "products", topics = AppConstants.PRODUCT_TOPIC, groupId = "product")
    public void onSaveProductEvent(ProductDto productDto) {
        log.info("Received Product: {}", productDto);
        productManageService.manage(productDto);
    }
}
