/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.cloud.discovery.reactive.enabled=false",
            "spring.cloud.discovery.enabled=false",
            "spring.cloud.config.enabled=false"
        },
        classes = ContainerConfig.class)
@AutoConfigureWebClient
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Autowired protected WebTestClient webTestClient;
}
