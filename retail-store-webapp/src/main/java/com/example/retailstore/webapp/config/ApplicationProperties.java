package com.example.retailstore.webapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "retailstore")
public record ApplicationProperties(String apiGatewayUrl) {}
