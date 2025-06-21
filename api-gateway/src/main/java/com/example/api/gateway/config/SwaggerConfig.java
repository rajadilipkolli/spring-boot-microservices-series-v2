/***
<p>
    Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.OAuthFlow;
import io.swagger.v3.oas.annotations.security.OAuthFlows;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.webflux.core.configuration.MultipleOpenApiSupportConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

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
        servers = @Server(url = "/"),
        security = {@SecurityRequirement(name = "Bearer"), @SecurityRequirement(name = "OAuth2")})
@SecurityScheme(
        name = "Bearer",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT Bearer Token Authentication")
@SecurityScheme(
        name = "OAuth2",
        type = SecuritySchemeType.OAUTH2,
        flows =
                @OAuthFlows(
                        authorizationCode =
                                @OAuthFlow(
                                        authorizationUrl =
                                                "${spring.security.oauth2.client.provider.keycloak.issuer-uri}/protocol/openid-connect/auth",
                                        tokenUrl =
                                                "${spring.security.oauth2.client.provider.keycloak.issuer-uri}/protocol/openid-connect/token")))
@AutoConfigureBefore(MultipleOpenApiSupportConfiguration.class)
class SwaggerConfig {

    private static final Logger log = LoggerFactory.getLogger(SwaggerConfig.class);

    @Bean
    @Lazy(value = false)
    List<GroupedOpenApi> groupedOpenApis(
            RouteDefinitionLocator locator, SwaggerUiConfigProperties swaggerUiConfigProperties) {
        Set<AbstractSwaggerUiConfigProperties.SwaggerUrl> swaggerUrls =
                swaggerUiConfigProperties.getUrls();

        return locator.getRouteDefinitions()
                .toStream()
                .map(RouteDefinition::getId)
                .filter(this::isServiceRoute)
                .map(
                        routeDefinitionId -> {
                            String name = extractNameFromRouteDefinitionId(routeDefinitionId);
                            log.debug("Configuring API documentation for service: {}", name);

                            swaggerUrls.add(
                                    new AbstractSwaggerUiConfigProperties.SwaggerUrl(
                                            name,
                                            "/" + routeDefinitionId + "/v3/api-docs",
                                            routeDefinitionId));
                            return GroupedOpenApi.builder()
                                    .pathsToMatch("/" + name + "/**")
                                    .group(name)
                                    .displayName(name.toUpperCase() + " Service")
                                    .build();
                        })
                .toList();
    }

    private boolean isServiceRoute(String routeId) {
        return routeId.endsWith("-service") && !routeId.contains("actuator");
    }

    private String extractNameFromRouteDefinitionId(String routeDefinitionId) {
        return routeDefinitionId.replace("-service", "");
    }
}
