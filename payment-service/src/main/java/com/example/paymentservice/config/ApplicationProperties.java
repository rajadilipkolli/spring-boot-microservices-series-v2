/*** Licensed under MIT License Copyright (c) 2022-2024 Raja Kolli. ***/
package com.example.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties("application")
public class ApplicationProperties {

    @NestedConfigurationProperty private Cors cors = new Cors();

    public ApplicationProperties() {}

    public Cors getCors() {
        return this.cors;
    }

    public void setCors(final Cors cors) {
        this.cors = cors;
    }

    public static class Cors {
        private String pathPattern = "/api/**";
        private String allowedMethods = "*";
        private String allowedHeaders = "*";
        private String allowedOriginPatterns = "*";
        private boolean allowCredentials = true;

        public Cors() {}

        public String getPathPattern() {
            return this.pathPattern;
        }

        public String getAllowedMethods() {
            return this.allowedMethods;
        }

        public String getAllowedHeaders() {
            return this.allowedHeaders;
        }

        public String getAllowedOriginPatterns() {
            return this.allowedOriginPatterns;
        }

        public boolean isAllowCredentials() {
            return this.allowCredentials;
        }

        public void setPathPattern(final String pathPattern) {
            this.pathPattern = pathPattern;
        }

        public void setAllowedMethods(final String allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public void setAllowedHeaders(final String allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public void setAllowedOriginPatterns(final String allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        public void setAllowCredentials(final boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public String toString() {
            return "ApplicationProperties.Cors(pathPattern="
                    + this.getPathPattern()
                    + ", allowedMethods="
                    + this.getAllowedMethods()
                    + ", allowedHeaders="
                    + this.getAllowedHeaders()
                    + ", allowedOriginPatterns="
                    + this.getAllowedOriginPatterns()
                    + ", allowCredentials="
                    + this.isAllowCredentials()
                    + ")";
        }
    }
}
