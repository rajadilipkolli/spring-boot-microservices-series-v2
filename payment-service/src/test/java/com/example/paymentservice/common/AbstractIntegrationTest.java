/*** Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli. ***/
package com.example.paymentservice.common;

import static com.example.paymentservice.utils.AppConstants.PROFILE_TEST;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import com.example.paymentservice.PaymentApplication;
import com.example.paymentservice.model.payload.OrderDto;
import com.example.paymentservice.repositories.CustomerDAO;
import com.example.paymentservice.repositories.CustomerRepository;
import com.example.paymentservice.repositories.LedgerRepository;
import com.example.paymentservice.repositories.OutboxEventRepository;
import com.example.paymentservice.repositories.PaymentOperationRepository;
import com.example.paymentservice.services.CustomerService;
import com.example.paymentservice.services.OrderServiceProxy;
import com.example.paymentservice.services.OutboxPublisher;
import com.example.paymentservice.services.PaymentOperationEngine;
import com.example.paymentservice.services.PaymentReconciler;
import com.example.paymentservice.services.listener.KafkaListenerConfig;
import com.example.paymentservice.services.provider.InternalLedgerAdapter;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles({PROFILE_TEST})
@SpringBootTest(
        webEnvironment = RANDOM_PORT,
        properties = {"spring.cloud.config.enabled=false", "eureka.client.enabled=false"},
        classes = {PaymentApplication.class, SQLContainerConfig.class, NonSQLContainerConfig.class})
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @Autowired protected MockMvc mockMvc;

    @Autowired protected MockMvcTester mockMvcTester;

    @Autowired protected JsonMapper jsonMapper;

    @Autowired protected KafkaTemplate<String, OrderDto> kafkaTemplate;

    @MockitoSpyBean protected CustomerRepository customerRepository;

    @Autowired protected CustomerDAO customerDAO;

    @Autowired protected KafkaListenerConfig kafkaListenerConfig;

    @Autowired protected CustomerService customerService;

    @MockitoBean protected OrderServiceProxy orderServiceProxy;

    @Autowired protected PaymentOperationRepository paymentOperationRepository;

    @Autowired protected LedgerRepository ledgerRepository;

    @Autowired protected OutboxEventRepository outboxEventRepository;

    @Autowired protected PaymentOperationEngine paymentOperationEngine;

    @Autowired protected DSLContext dslContext;

    @Autowired protected OutboxPublisher outboxPublisher;

    @Autowired protected PaymentReconciler paymentReconciler;

    @Autowired protected PlatformTransactionManager transactionManager;

    @Autowired protected InternalLedgerAdapter ledgerAdapter;

    @MockitoSpyBean protected OutboxEventRepository outboxRepositorySpy;
}
