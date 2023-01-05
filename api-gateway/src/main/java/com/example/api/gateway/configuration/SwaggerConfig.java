/* Licensed under Apache-2.0 2022 */
package com.example.api.gateway.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import java.util.List;
import java.util.Set;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info =
        @Info(
                title = "api-gateway",
                description = "Documentation for all the Microservices in Demo Application",
                version = "v1",
                contact =
                @Contact(
                        name = "Raja Kolli",
                        url = "https://github.com/rajadilipkolli")),
        servers = @Server(url = "/"))
public class SwaggerConfig {

    @Bean
    public List<GroupedOpenApi> apis(
            RouteDefinitionLocator locator, SwaggerUiConfigProperties swaggerUiConfigProperties) {
        Set<AbstractSwaggerUiConfigProperties.SwaggerUrl> finalUrls =
                swaggerUiConfigProperties.getUrls();
        return locator.getRouteDefinitions()
                .filter(routeDefinition -> routeDefinition.getId().matches(".*-service"))
                .map(
                        routeDefinition -> {
                            String routeDefinitionId = routeDefinition.getId();
                            String name = routeDefinitionId.replaceAll("-service", "");

                            finalUrls.add(
                                    new AbstractSwaggerUiConfigProperties.SwaggerUrl(
                                            name,
                                            "/" + routeDefinitionId + "/v3/api-docs",
                                            routeDefinitionId));
                            return GroupedOpenApi.builder()
                                    .pathsToMatch("/" + name + "/**")
                                    .group(name)
                                    .build();
                        })
                .toStream()
                .toList();
    }
}
