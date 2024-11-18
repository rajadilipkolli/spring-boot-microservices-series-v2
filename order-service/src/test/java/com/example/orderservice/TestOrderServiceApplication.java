/***
<p>
    Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli.
</p>
***/

package com.example.orderservice;

import com.example.orderservice.common.ContainersConfig;
import com.example.orderservice.common.PostGreSQLContainer;
import com.example.orderservice.utils.AppConstants;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

@ImportTestcontainers(PostGreSQLContainer.class)
public class TestOrderServiceApplication {

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", AppConstants.PROFILE_LOCAL);
        SpringApplication.from(OrderServiceApplication::main)
                .with(ContainersConfig.class)
                .run(args);
    }
}
