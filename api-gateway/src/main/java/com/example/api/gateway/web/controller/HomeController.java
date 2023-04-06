/* Licensed under Apache-2.0 2023 */
package com.example.api.gateway.web.controller;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
public class HomeController {

    @Bean
    RouterFunction<ServerResponse> routerFunction() {
        return route(
                GET("/"),
                req -> ServerResponse.temporaryRedirect(URI.create("swagger-ui.html")).build());
    }
}
