/*** Licensed under Apache-2.0 2021-2023 ***/
package com.example.catalogservice.config;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("application")
public class ApplicationProperties {

    private String inventoryServiceUrl;
}
