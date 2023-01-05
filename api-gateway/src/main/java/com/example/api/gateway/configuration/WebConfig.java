/* Licensed under Apache-2.0 2021 */
package com.example.api.gateway.configuration;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@Profile("cors")
public class WebConfig {

    @Bean
    CorsWebFilter corsWebFilter() {
        final CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOrigins(List.of("localhost", "mytrustedwebsite.com"));
        corsConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS", "HEAD", "PUT"));
        corsConfig.addAllowedHeader("Access-Control-Allow-Origin");

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}
