/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.config;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties("application")
public class ApplicationProperties {

    private String orderServiceUrl;
    private boolean byPassCircuitBreaker;

    @NestedConfigurationProperty private Cors cors = new Cors();
    @NestedConfigurationProperty private Operation operation = new Operation();
    @NestedConfigurationProperty private Outbox outbox = new Outbox();
    @NestedConfigurationProperty private Reconciler reconciler = new Reconciler();

    public ApplicationProperties() {}

    /**
     * Checks that the operation lease exceeds the combined provider and statement timeouts.
     *
     * @throws IllegalStateException if the lease is less than or equal to that sum
     */
    @PostConstruct
    public void validate() {
        Duration totalTimeout =
                operation.getProviderTimeout().plus(operation.getStatementTimeout());
        if (operation.getLeaseDuration().compareTo(totalTimeout) <= 0) {
            throw new IllegalStateException(
                    "Operation leaseDuration ("
                            + operation.getLeaseDuration()
                            + ") must exceed providerTimeout ("
                            + operation.getProviderTimeout()
                            + ") + statementTimeout ("
                            + operation.getStatementTimeout()
                            + ")");
        }
    }

    public String getOrderServiceUrl() {
        return this.orderServiceUrl;
    }

    public void setOrderServiceUrl(String orderServiceUrl) {
        this.orderServiceUrl = orderServiceUrl;
    }

    public boolean isByPassCircuitBreaker() {
        return this.byPassCircuitBreaker;
    }

    public void setByPassCircuitBreaker(boolean byPassCircuitBreaker) {
        this.byPassCircuitBreaker = byPassCircuitBreaker;
    }

    public Cors getCors() {
        return this.cors;
    }

    public void setCors(final Cors cors) {
        this.cors = cors;
    }

    public Operation getOperation() {
        return operation;
    }

    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox outbox) {
        this.outbox = outbox;
    }

    public Reconciler getReconciler() {
        return reconciler;
    }

    public void setReconciler(Reconciler reconciler) {
        this.reconciler = reconciler;
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

        public void setPathPattern(final String pathPattern) {
            this.pathPattern = pathPattern;
        }

        public String getAllowedMethods() {
            return this.allowedMethods;
        }

        public void setAllowedMethods(final String allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public String getAllowedHeaders() {
            return this.allowedHeaders;
        }

        public void setAllowedHeaders(final String allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public String getAllowedOriginPatterns() {
            return this.allowedOriginPatterns;
        }

        public void setAllowedOriginPatterns(final String allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        public boolean isAllowCredentials() {
            return this.allowCredentials;
        }

        public void setAllowCredentials(final boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }
    }

    public static class Operation {
        private Duration leaseDuration = Duration.ofSeconds(10);
        private Duration providerTimeout = Duration.ofSeconds(3);
        private Duration statementTimeout = Duration.ofSeconds(2);
        private Duration lockTimeout = Duration.ofSeconds(2);
        private int maxAttempts = 3;

        public Duration getLeaseDuration() {
            return leaseDuration;
        }

        public void setLeaseDuration(Duration leaseDuration) {
            this.leaseDuration = leaseDuration;
        }

        public Duration getProviderTimeout() {
            return providerTimeout;
        }

        public void setProviderTimeout(Duration providerTimeout) {
            this.providerTimeout = providerTimeout;
        }

        public Duration getStatementTimeout() {
            return statementTimeout;
        }

        public void setStatementTimeout(Duration statementTimeout) {
            this.statementTimeout = statementTimeout;
        }

        public Duration getLockTimeout() {
            return lockTimeout;
        }

        public void setLockTimeout(Duration lockTimeout) {
            this.lockTimeout = lockTimeout;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }

    public static class Outbox {
        private boolean enabled = true;
        private Duration publishDelay = Duration.ofMillis(500);
        private Duration lockTimeout = Duration.ofSeconds(30);
        private int maxRetries = 3;
        private int batchSize = 100;
        private Duration sendTimeout = Duration.ofSeconds(5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getPublishDelay() {
            return publishDelay;
        }

        public void setPublishDelay(Duration publishDelay) {
            this.publishDelay = publishDelay;
        }

        public Duration getLockTimeout() {
            return lockTimeout;
        }

        public void setLockTimeout(Duration lockTimeout) {
            this.lockTimeout = lockTimeout;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public Duration getSendTimeout() {
            return sendTimeout;
        }

        public void setSendTimeout(Duration sendTimeout) {
            this.sendTimeout = sendTimeout;
        }
    }

    public static class Reconciler {
        private boolean enabled = true;
        private Duration delay = Duration.ofSeconds(1);
        private int batchSize = 50;
        private Duration initialBackoff = Duration.ofSeconds(1);
        private Duration maxBackoff = Duration.ofSeconds(10);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getDelay() {
            return delay;
        }

        public void setDelay(Duration delay) {
            this.delay = delay;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }
    }
}
