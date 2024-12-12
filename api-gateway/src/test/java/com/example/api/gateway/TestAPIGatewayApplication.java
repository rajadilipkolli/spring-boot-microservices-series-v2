/***
<p>
    Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli.
</p>
***/

package com.example.api.gateway;

import com.example.api.gateway.config.ContainerConfig;
import org.springframework.boot.SpringApplication;

public class TestAPIGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.from(APIGatewayApplication::main).with(ContainerConfig.class).run(args);
    }
}
