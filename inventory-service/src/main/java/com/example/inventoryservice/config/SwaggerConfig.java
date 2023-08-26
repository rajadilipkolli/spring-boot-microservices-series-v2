/* Licensed under Apache-2.0 2022-2023 */
package com.example.inventoryservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "inventory-service", version = "v1"),
        servers = @Server(url = "${server.servlet.contextPath}"))
public class SwaggerConfig {}
