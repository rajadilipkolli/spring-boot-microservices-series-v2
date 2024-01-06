/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice.common;

import static com.example.inventoryservice.utils.AppConstants.PROFILE_TEST;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.example.inventoryservice.TestInventoryApplication;
import com.example.inventoryservice.config.TestStockOrderListenerConfig;
import com.example.inventoryservice.repositories.InventoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles({PROFILE_TEST})
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        classes = {TestInventoryApplication.class, TestStockOrderListenerConfig.class})
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @Autowired protected MockMvc mockMvc;

    @Autowired protected ObjectMapper objectMapper;

    @Autowired protected KafkaTemplate<Long, Object> kafkaTemplate;

    @Autowired protected InventoryRepository inventoryRepository;

    @Autowired protected TestStockOrderListenerConfig stockOrderListener;
}
