/* Licensed under Apache-2.0 2022 */
package com.example.orderservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "order-service",
                        version = "v1",
                        description = "APIs related to Orders"))
public class SwaggerConfig {

    @Bean
    ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}
