/***
<p>
    Licensed under MIT License Copyright (c) 2022-2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Catalog Service API",
                version = "v1",
                description = "Swagger documentation for inventory Service"
        )
)
class SwaggerConfig {}
