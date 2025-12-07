/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.catalogservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("application")
@Validated
public record ApplicationProperties(
        @NotBlank(message = "Inventory Service URL cannot be blank") String inventoryServiceUrl,
        @NestedConfigurationProperty @Valid Cors cors,
        @Valid Resilience resilience) {

    public ApplicationProperties {
        // Default values for nested properties
        if (cors == null) {
            cors = new Cors();
        }
        if (resilience == null) {
            resilience = new Resilience();
        }
    }

    public static class Cors {

        private String pathPattern = "/api/**";
        private String allowedMethods = "*";
        private String allowedHeaders = "*";
        private String allowedOriginPatterns = "*";
        private boolean allowCredentials = true;

        public String getPathPattern() {
            return pathPattern;
        }

        public void setPathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
        }

        public String getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(String allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public String getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(String allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public String getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        public void setAllowedOriginPatterns(String allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }
    }

    public static class Resilience {
        private int retryAttempts = 3;
        private long retryDelayMs = 1000;
        private double circuitBreakerFailureThreshold = 0.5;
        private int circuitBreakerMinimumCalls = 10;
        private long circuitBreakerWaitDurationMs = 30000;

        // Getters and setters
        public int getRetryAttempts() {
            return retryAttempts;
        }

        public void setRetryAttempts(int retryAttempts) {
            this.retryAttempts = retryAttempts;
        }

        public long getRetryDelayMs() {
            return retryDelayMs;
        }

        public void setRetryDelayMs(long retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
        }

        public double getCircuitBreakerFailureThreshold() {
            return circuitBreakerFailureThreshold;
        }

        public void setCircuitBreakerFailureThreshold(double circuitBreakerFailureThreshold) {
            this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
        }

        public int getCircuitBreakerMinimumCalls() {
            return circuitBreakerMinimumCalls;
        }

        public void setCircuitBreakerMinimumCalls(int circuitBreakerMinimumCalls) {
            this.circuitBreakerMinimumCalls = circuitBreakerMinimumCalls;
        }

        public long getCircuitBreakerWaitDurationMs() {
            return circuitBreakerWaitDurationMs;
        }

        public void setCircuitBreakerWaitDurationMs(long circuitBreakerWaitDurationMs) {
            this.circuitBreakerWaitDurationMs = circuitBreakerWaitDurationMs;
        }
    }
}
