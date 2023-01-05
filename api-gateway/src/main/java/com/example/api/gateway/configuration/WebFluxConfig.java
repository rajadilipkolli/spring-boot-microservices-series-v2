/* Licensed under Apache-2.0 2021 */
package com.example.api.gateway.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedMethods("GET", "POST", "OPTIONS", "HEAD", "PUT")
                .allowedHeaders("Access-Control-Allow-Origin")
                .allowedOrigins("http://localhost:8765", "mytrustedwebsite.com")
                .allowCredentials(true);
    }
}
