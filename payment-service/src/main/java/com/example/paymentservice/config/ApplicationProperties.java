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

    /** Returns the payment operation settings. */
    public Operation getOperation() {
        return operation;
    }

    /** Sets the payment operation settings. */
    public void setOperation(Operation operation) {
        this.operation = operation;
    }

    /** Returns the outbox publication settings. */
    public Outbox getOutbox() {
        return outbox;
    }

    /** Sets the outbox publication settings. */
    public void setOutbox(Outbox outbox) {
        this.outbox = outbox;
    }

    /** Returns the reconciliation settings. */
    public Reconciler getReconciler() {
        return reconciler;
    }

    /** Sets the reconciliation settings. */
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

        /** Sets the URL pattern to which CORS settings apply. */
        public void setPathPattern(final String pathPattern) {
            this.pathPattern = pathPattern;
        }

        public String getAllowedMethods() {
            return this.allowedMethods;
        }

        /** Sets the allowed CORS methods. */
        public void setAllowedMethods(final String allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public String getAllowedHeaders() {
            return this.allowedHeaders;
        }

        /** Sets the allowed CORS headers. */
        public void setAllowedHeaders(final String allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public String getAllowedOriginPatterns() {
            return this.allowedOriginPatterns;
        }

        /** Sets the allowed CORS origin patterns. */
        public void setAllowedOriginPatterns(final String allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        public boolean isAllowCredentials() {
            return this.allowCredentials;
        }

        /** Sets whether CORS requests may include credentials. */
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

        /** Returns the duration of an operation ownership lease. */
        public Duration getLeaseDuration() {
            return leaseDuration;
        }

        /** Sets the duration of an operation ownership lease. */
        public void setLeaseDuration(Duration leaseDuration) {
            this.leaseDuration = leaseDuration;
        }

        /** Returns the maximum wait for provider execution or lookup. */
        public Duration getProviderTimeout() {
            return providerTimeout;
        }

        /** Sets the maximum wait for provider execution or lookup. */
        public void setProviderTimeout(Duration providerTimeout) {
            this.providerTimeout = providerTimeout;
        }

        /** Returns the PostgreSQL statement timeout for provider transactions. */
        public Duration getStatementTimeout() {
            return statementTimeout;
        }

        /** Sets the PostgreSQL statement timeout for provider transactions. */
        public void setStatementTimeout(Duration statementTimeout) {
            this.statementTimeout = statementTimeout;
        }

        /** Returns the timeout for acquiring a PostgreSQL lock. */
        public Duration getLockTimeout() {
            return lockTimeout;
        }

        /** Sets the timeout for acquiring a PostgreSQL lock. */
        public void setLockTimeout(Duration lockTimeout) {
            this.lockTimeout = lockTimeout;
        }

        /** Returns the attempt limit checked before reconciliation re-executes an operation. */
        public int getMaxAttempts() {
            return maxAttempts;
        }

        /** Sets the attempt limit checked before reconciliation re-executes an operation. */
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

        /** Returns whether scheduled outbox publication and reaping are enabled. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Sets whether scheduled outbox publication and reaping are enabled. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Returns the delay between scheduled outbox publication batches. */
        public Duration getPublishDelay() {
            return publishDelay;
        }

        /** Sets the delay between scheduled outbox publication batches. */
        public void setPublishDelay(Duration publishDelay) {
            this.publishDelay = publishDelay;
        }

        /** Returns the age after which a processing outbox event can be reclaimed. */
        public Duration getLockTimeout() {
            return lockTimeout;
        }

        /** Sets the age after which a processing outbox event can be reclaimed. */
        public void setLockTimeout(Duration lockTimeout) {
            this.lockTimeout = lockTimeout;
        }

        /** Returns the retry limit for outbox publication. */
        public int getMaxRetries() {
            return maxRetries;
        }

        /** Sets the retry limit for outbox publication. */
        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        /** Returns the maximum number of outbox events to claim per publication batch. */
        public int getBatchSize() {
            return batchSize;
        }

        /** Sets the maximum number of outbox events to claim per publication batch. */
        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        /** Returns the maximum wait for a Kafka send to complete. */
        public Duration getSendTimeout() {
            return sendTimeout;
        }

        /** Sets the maximum wait for a Kafka send to complete. */
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

        /** Returns whether scheduled reconciliation is enabled. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Sets whether scheduled reconciliation is enabled. */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** Returns the delay between scheduled reconciliation batches. */
        public Duration getDelay() {
            return delay;
        }

        /** Sets the delay between scheduled reconciliation batches. */
        public void setDelay(Duration delay) {
            this.delay = delay;
        }

        /** Returns the maximum number of operations to claim per reconciliation batch. */
        public int getBatchSize() {
            return batchSize;
        }

        /** Sets the maximum number of operations to claim per reconciliation batch. */
        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        /** Returns the initial delay used when deferring reconciliation. */
        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        /** Sets the initial delay used when deferring reconciliation. */
        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        /** Returns the maximum reconciliation backoff duration. */
        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        /** Sets the maximum reconciliation backoff duration. */
        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }
    }
}
