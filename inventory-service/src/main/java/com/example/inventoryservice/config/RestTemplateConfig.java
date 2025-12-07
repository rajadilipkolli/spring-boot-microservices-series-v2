/***
<p>
    Licensed under MIT License Copyright (c) 2022-2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config;

import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
class RestTemplateConfig {

    // IMPORTANT! To instrument RestTemplate you must inject the RestTemplateBuilder
    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
