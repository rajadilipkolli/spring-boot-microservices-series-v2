/* Licensed under Apache-2.0 2021-2023 */
package com.example.inventoryservice.config;

import com.example.inventoryservice.config.ApplicationProperties.Cors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApplicationProperties properties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        Cors cors = properties.getCors();
        registry.addMapping(cors.getPathPattern())
                .allowedMethods(cors.getAllowedHeaders())
                .allowedHeaders(cors.getAllowedHeaders())
                .allowedOriginPatterns(cors.getAllowedOriginPatterns())
                .allowCredentials(cors.isAllowCredentials());
    }
}
