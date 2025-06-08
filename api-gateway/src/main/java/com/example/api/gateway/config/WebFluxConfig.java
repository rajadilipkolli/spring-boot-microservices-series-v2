/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration(proxyBeanMethods = false)
class WebFluxConfig implements WebFluxConfigurer {

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedMethods("GET", "POST", "OPTIONS", "HEAD", "PUT")
                .allowedHeaders("Access-Control-Allow-Origin")
                .allowedOrigins("http://localhost:8765", "mytrustedwebsite.com")
                .allowCredentials(true);
    }
}
