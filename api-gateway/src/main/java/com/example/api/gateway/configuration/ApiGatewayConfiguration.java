package com.example.api.gateway.configuration;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiGatewayConfiguration {

    @Bean
    public RouteLocator gatewayRouter(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(
                        p ->
                                p.path("/get")
                                        .filters(
                                                f ->
                                                        f.addRequestHeader("MyHeader", "MyURI")
                                                                .addRequestParameter(
                                                                        "Param", "MyValue"))
                                        .uri("http://httpbin.org:80"))
                .route(p -> p.path("/catalog-service/**").uri("lb://catalog-service"))
                .route(p -> p.path("/inventory-service/**").uri("lb://inventory-service"))
                .route(p -> p.path("/order-service/**").uri("lb://order-service"))
                //				.route(p -> p.path("/ORDER-SERVICE-new/**")
                //						.filters(f -> f.rewritePath(
                //								"/ORDER-SERVICE-new/(?<segment>.*)",
                //								"/ORDER-SERVICE-feign/${segment}"))
                //						.uri("lb://ORDER-SERVICE"))
                .build();
    }
}
