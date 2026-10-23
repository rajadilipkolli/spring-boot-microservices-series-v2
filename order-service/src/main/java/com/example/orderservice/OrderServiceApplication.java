/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.orderservice;

import com.example.orderservice.config.ApplicationProperties;
import com.example.orderservice.config.OrderServiceRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@EnableConfigurationProperties({ApplicationProperties.class})
@ImportRuntimeHints(OrderServiceRuntimeHints.class)
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
