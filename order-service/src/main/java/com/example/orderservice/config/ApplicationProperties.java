/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("application")
@Validated
public record ApplicationProperties(
        @NotBlank(message = "CatalogServiceUrl Cant be Blank") String catalogServiceUrl,
        ResiliencePropertyHolder resiliencePropertyHolder,
        @NestedConfigurationProperty Cors cors) {

    public static final boolean CATALOG_EXISTS_DEFAULT_VALUE =
            ResiliencePropertyHolder.CATALOG_EXISTS_RETURN_VALUE;

    // Ref : https://stackoverflow.com/a/75221370/5557885
    private static class ResiliencePropertyHolder {

        private static boolean CATALOG_EXISTS_RETURN_VALUE;

        public ResiliencePropertyHolder(boolean catalogExistsDefaultValue) {
            CATALOG_EXISTS_RETURN_VALUE = catalogExistsDefaultValue;
        }
    }

    public ApplicationProperties {
        cors = new Cors();
    }
}
