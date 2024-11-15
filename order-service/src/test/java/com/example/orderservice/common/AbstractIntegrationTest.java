/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.common;

import com.example.orderservice.utils.AppConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles({AppConstants.PROFILE_TEST})
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.cloud.config.enabled=false"},
        classes = ContainersConfig.class)
@ImportTestcontainers(PostGreSQLContainer.class)
@AutoConfigureMockMvc
@AutoConfigureObservability
public abstract class AbstractIntegrationTest extends ContainerInitializer {

    @Autowired protected MockMvc mockMvc;

    @Autowired protected ObjectMapper objectMapper;

    @TestConfiguration
    static class ObservationTestConfiguration {
        @Bean
        TestObservationRegistry observationRegistry() {
            return TestObservationRegistry.create();
        }

        @Bean
        SimpleTracer simpleTracer() {
            return new SimpleTracer();
        }
    }
}
