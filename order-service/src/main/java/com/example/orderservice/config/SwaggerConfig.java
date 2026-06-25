/***
<p>
    Licensed under MIT License Copyright (c) 2022-2024 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Catalog Service API",
                version = "v1",
                description = "Swagger documentation for Catalog Service"
        )
)
class SwaggerConfig {

    @Bean
    ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}
