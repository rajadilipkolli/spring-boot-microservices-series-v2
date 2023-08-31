/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("application")
@Validated
public record ApplicationProperties(
        @NotBlank(message = "CatalogServiceUrl Cant be Blank") String catalogServiceUrl,
        @NestedConfigurationProperty Cors cors) {

    public ApplicationProperties {
        cors = new Cors();
    }
}

@Data
class Cors {
    private String pathPattern = "/api/**";
    private String allowedMethods = "*";
    private String allowedHeaders = "*";
    private String allowedOriginPatterns = "*";
    private boolean allowCredentials = true;
}
