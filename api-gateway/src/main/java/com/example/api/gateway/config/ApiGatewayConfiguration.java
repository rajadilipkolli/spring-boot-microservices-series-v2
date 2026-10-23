/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for custom programmatic routes in Spring Cloud Gateway.
 *
 * <p>NOTE: The routes declared below (/get and /ORDER-SERVICE-new/**) are maintained as
 * demonstration and contract integration testing routes (validated by
 * ApiGatewayConfigurationIntegrationTest). Core microservice business routes are declared
 * declaratively in application.yml.
 */
@Configuration(proxyBeanMethods = false)
class ApiGatewayConfiguration {

    @Bean
    RouteLocator gatewayRouter(
            RouteLocatorBuilder builder,
            @Value("${app.gateway.httpbin:http://httpbin.org:80}") String httpbinUri) {
        return builder.routes()
                .route(
                        routeSpec ->
                                routeSpec
                                        .path("/get")
                                        .filters(
                                                filterSpec ->
                                                        filterSpec
                                                                .addRequestHeader(
                                                                        "MyHeader", "MyURI")
                                                                .addRequestParameter(
                                                                        "Param", "MyValue")
                                                                .addResponseHeader(
                                                                        "X-Response-Header",
                                                                        "Transformed"))
                                        .uri(httpbinUri))
                .route(
                        p ->
                                p.path("/ORDER-SERVICE-new/**")
                                        .filters(
                                                f ->
                                                        f.rewritePath(
                                                                "/ORDER-SERVICE-new/(?<segment>.*)",
                                                                "/ORDER-SERVICE-feign/${segment}"))
                                        .uri("lb://ORDER-SERVICE"))
                .build();
    }
}
