/* Licensed under Apache-2.0 2022 */
package com.example.api.gateway.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.GroupedOpenApi;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "api-gateway", version = "v1"))
@RequiredArgsConstructor
public class SwaggerConfig {

    private final RouteDefinitionLocator locator;

    @Bean
    public List<GroupedOpenApi> apis() {
        List<GroupedOpenApi> groups = new ArrayList<>();
        List<RouteDefinition> definitions = locator.getRouteDefinitions().collectList().block();
        definitions.stream()
                .filter(routeDefinition -> routeDefinition.getId().matches(".*-service"))
                .forEach(
                        routeDefinition -> {
                            String name = routeDefinition.getId().replaceAll("-service", "");
                            GroupedOpenApi.builder()
                                    .pathsToMatch("/" + name + "/**")
                                    .group(name)
                                    .build();
                        });
        return groups;
    }
}
