/* Licensed under Apache-2.0 2021-2022 */
package com.example.orderservice.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("application")
@Validated
public record ApplicationProperties(
        @NotBlank(message = "CatalogServiceUrl Cant be Blank") String catalogServiceUrl) {}
