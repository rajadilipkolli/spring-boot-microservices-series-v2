/* Licensed under Apache-2.0 2022 */
package com.example.api.gateway.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import java.util.ArrayList;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
@OpenAPIDefinition(info = @Info(title = "api-gateway", version = "v1"))
public class SwaggerConfig {

    // return type should be a list, cant be mono or flux.
    @Bean
    @Lazy(false)
    public List<GroupedOpenApi> apis(RouteDefinitionLocator locator) {
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
        // we need api-gateway as well in the swagger hence manually
        GroupedOpenApi.builder().pathsToMatch("/**").group("api-gateway").build();
        return groups;
    }
}
