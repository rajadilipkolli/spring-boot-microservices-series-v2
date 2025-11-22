/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.common;

import static com.example.inventoryservice.utils.AppConstants.PROFILE_TEST;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.example.inventoryservice.config.TestStockOrderListenerConfig;
import com.example.inventoryservice.repositories.InventoryJOOQRepository;
import com.example.inventoryservice.repositories.InventoryRepository;
import com.example.inventoryservice.services.InventoryOrderManageService;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired protected KafkaTemplate<Long, Object> kafkaTemplate;

    @Autowired protected InventoryJOOQRepository inventoryJOOQRepository;

    @Autowired protected InventoryRepository inventoryRepository;

    @Autowired protected TestStockOrderListenerConfig stockOrderListener;

    @Autowired protected InventoryOrderManageService inventoryOrderManageService;
}
