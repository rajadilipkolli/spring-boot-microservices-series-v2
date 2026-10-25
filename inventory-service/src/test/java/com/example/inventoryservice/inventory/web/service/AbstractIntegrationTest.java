/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import static com.example.inventoryservice.inventory.utils.AppConstants.PROFILE_TEST;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.example.inventoryservice.inventory.InventoryOrderManageService;
import com.example.inventoryservice.inventory.shared.NonSQLContainersConfig;
import com.example.inventoryservice.inventory.shared.SQLContainersConfig;
import com.example.inventoryservice.inventory.shared.TestStockOrderListenerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles({PROFILE_TEST})
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        classes = {
            SQLContainersConfig.class,
            NonSQLContainersConfig.class,
            TestStockOrderListenerConfig.class
        })
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @Autowired protected MockMvc mockMvc;

    @Autowired protected JsonMapper jsonMapper;

    @Autowired protected KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired protected InventoryJOOQRepository inventoryJOOQRepository;

    @Autowired protected InventoryRepository inventoryRepository;

    @Autowired protected ProcessedOrderEventRepository processedOrderEventRepository;

    @Autowired protected TestStockOrderListenerConfig stockOrderListener;

    @Autowired protected KafkaConnectionDetails kafkaConnectionDetails;

    @Autowired protected InventoryOrderManageService inventoryOrderManageService;
}
