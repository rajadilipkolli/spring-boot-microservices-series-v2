/*** Licensed under Apache-2.0 2021-2023 ***/
package com.example.catalogservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class WebFluxConfig implements WebFluxConfigurer {

    private final ApplicationProperties properties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        ApplicationProperties.Cors cors = properties.getCors();
        registry.addMapping(cors.getPathPattern())
                .allowedMethods(cors.getAllowedMethods())
                .allowedHeaders(cors.getAllowedHeaders())
                .allowedOriginPatterns(cors.getAllowedOriginPatterns())
                .allowCredentials(cors.isAllowCredentials());
    }
}
