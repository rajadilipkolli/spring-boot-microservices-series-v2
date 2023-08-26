/*** Licensed under Apache-2.0 2021-2023 ***/
package com.example.catalogservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties("application")
public class ApplicationProperties {

    private String inventoryServiceUrl;

    @NestedConfigurationProperty private Cors cors = new Cors();

    @Data
    public static class Cors {
        private String pathPattern = "/api/**";
        private String allowedMethods = "*";
        private String allowedHeaders = "*";
        private String allowedOriginPatterns = "*";
        private boolean allowCredentials = true;
    }
}
