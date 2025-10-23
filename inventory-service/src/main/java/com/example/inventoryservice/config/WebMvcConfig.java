/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config;

import com.example.inventoryservice.config.ApplicationProperties.Cors;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebMvcConfig implements WebMvcConfigurer {

    private final ApplicationProperties properties;

    WebMvcConfig(ApplicationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        Cors cors = properties.getCors();
        registry.addMapping(cors.getPathPattern())
                .allowedMethods(cors.getAllowedMethods().split(","))
                .allowedHeaders(cors.getAllowedHeaders().split(","))
                .allowedOriginPatterns(cors.getAllowedOriginPatterns().split(","))
                .allowCredentials(cors.isAllowCredentials())
                .maxAge(3600); // Cache preflight response for 1 hour
    }
}
