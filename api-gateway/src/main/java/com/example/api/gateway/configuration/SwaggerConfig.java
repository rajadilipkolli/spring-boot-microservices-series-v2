/* Licensed under Apache-2.0 2022 */
package com.example.api.gateway.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "api-gateway", version = "v1"))
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi orderServiceOpenApi() {
        String[] paths = {"/order-service/**"};
        return GroupedOpenApi.builder().group("order-service").pathsToMatch(paths)
                .build();
    }

    @Bean
    public GroupedOpenApi apiGatewayOpenApi() {
        String[] paths = {"/api-gateway/**"};
        return GroupedOpenApi.builder().group("api-gateway").pathsToMatch(paths)
                .build();
    }
}
