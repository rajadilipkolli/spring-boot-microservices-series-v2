/* Licensed under Apache-2.0 2022 */
package com.example.api.gateway.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import java.util.Comparator;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "api-gateway", version = "v1"))
public class SwaggerConfig {

    // return type should be a list, cant be mono or flux.
    @Bean
    public List<GroupedOpenApi> apis(RouteDefinitionLocator locator) {
        var groupedOpenApiList =
                new java.util.ArrayList<>(
                        locator.getRouteDefinitions()
                                .filter(
                                        routeDefinition ->
                                                routeDefinition.getId().matches(".*-service"))
                                .map(
                                        routeDefinition -> {
                                            String name =
                                                    routeDefinition
                                                            .getId()
                                                            .replaceAll("-service", "");
                                            return GroupedOpenApi.builder()
                                                    .pathsToMatch("/" + name + "/**")
                                                    .group(name)
                                                    .build();
                                        })
                                .toStream()
                                .toList());
        // we need api-gateway as well in the swagger hence manually
        groupedOpenApiList.add(
                GroupedOpenApi.builder().pathsToMatch("/**").group("api-gateway").build());
        groupedOpenApiList.sort(Comparator.comparing(GroupedOpenApi::getGroup));
        return groupedOpenApiList;
    }
}
