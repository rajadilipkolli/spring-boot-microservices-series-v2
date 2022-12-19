/* Licensed under Apache-2.0 2022 */
package com.example.inventoryservice.config;

import com.example.catalogservice.entities.Product;
import com.example.inventoryservice.services.InventoryOrderManageService;
import com.example.inventoryservice.services.ProductManageService;
import com.example.inventoryservice.utils.AppConstants;
import com.example.orderservice.dtos.OrderDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;

@EnableKafka
@Slf4j
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class KafkaListenerConfig {

    private final InventoryOrderManageService orderManageService;
    private final ProductManageService productManageService;

    @KafkaListener(id = "orders", topics = AppConstants.ORDERS_TOPIC, groupId = "stock")
    public void onEvent(OrderDto order) {
        log.info("Received Order: {}", order);
        if ("NEW".equals(order.getStatus())) {
            orderManageService.reserve(order);
        } else {
            orderManageService.confirm(order);
        }
    }

    @KafkaListener(id = "products", topics = AppConstants.PRODUCT_TOPIC, groupId = "product")
    public void onSaveProductEvent(Product product) {
        log.info("Received Product: {}", product);
        productManageService.manage(product);
    }
}
