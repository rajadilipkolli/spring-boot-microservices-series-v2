/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebFluxConfig implements WebFluxConfigurer {

    private final ApplicationProperties properties;

    public WebFluxConfig(ApplicationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        ApplicationProperties.Cors cors = properties.cors();
        registry.addMapping(cors.getPathPattern())
                .allowedMethods(cors.getAllowedMethods().split(","))
                .allowedHeaders(cors.getAllowedHeaders().split(","))
                .allowedOriginPatterns(cors.getAllowedOriginPatterns().split(","))
                .allowCredentials(cors.isAllowCredentials())
                .maxAge(3600);
    }
}
