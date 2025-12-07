/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("application")
@Validated
public record ApplicationProperties(
        @NotBlank(message = "CatalogServiceUrl Cant be Blank") String catalogServiceUrl,
        boolean byPassCircuitBreaker,
        @NestedConfigurationProperty @Valid Cors cors) {

    public ApplicationProperties {
        cors = new Cors();
    }
}
