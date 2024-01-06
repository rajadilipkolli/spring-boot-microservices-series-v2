/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApplicationProperties properties;

    public WebMvcConfig(ApplicationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        Cors cors = properties.cors();
        registry.addMapping(cors.getPathPattern())
                .allowedMethods(cors.getAllowedHeaders())
                .allowedHeaders(cors.getAllowedHeaders())
                .allowedOriginPatterns(cors.getAllowedOriginPatterns())
                .allowCredentials(cors.isAllowCredentials());
    }
}
