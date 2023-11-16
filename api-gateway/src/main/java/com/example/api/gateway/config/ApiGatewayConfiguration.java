/***
<p>
    Licensed under MIT License Copyright (c) 2021-2022 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiGatewayConfiguration {

    @Bean
    RouteLocator gatewayRouter(RouteLocatorBuilder builder) {
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
                                                                        "Param", "MyValue"))
                                        .uri("http://httpbin.org:80"))
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
