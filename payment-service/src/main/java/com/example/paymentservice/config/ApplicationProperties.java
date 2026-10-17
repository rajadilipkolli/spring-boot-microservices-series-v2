/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("application")
public record ApplicationProperties(
        String orderServiceUrl,
        boolean byPassCircuitBreaker,
        @NestedConfigurationProperty Cors cors,
        @NestedConfigurationProperty Operation operation,
        @NestedConfigurationProperty Outbox outbox,
        @NestedConfigurationProperty Reconciler reconciler) {

    @ConstructorBinding
    public ApplicationProperties(
            String orderServiceUrl,
            boolean byPassCircuitBreaker,
            @DefaultValue Cors cors,
            @DefaultValue Operation operation,
            @DefaultValue Outbox outbox,
            @DefaultValue Reconciler reconciler) {
        this.orderServiceUrl = orderServiceUrl;
        this.byPassCircuitBreaker = byPassCircuitBreaker;
        this.cors = cors != null ? cors : new Cors();
        this.operation = operation != null ? operation : new Operation();
        this.outbox = outbox != null ? outbox : new Outbox();
        this.reconciler = reconciler != null ? reconciler : new Reconciler();
        validate();
    }

    public ApplicationProperties() {
        this(null, false, new Cors(), new Operation(), new Outbox(), new Reconciler());
    }

    /**
     * Checks that the operation lease exceeds the combined provider and statement timeouts.
     *
     * @throws IllegalStateException if the lease is less than or equal to that sum
     */
    private void validate() {
        Duration totalTimeout = operation.providerTimeout().plus(operation.statementTimeout());
        if (operation.leaseDuration().compareTo(totalTimeout) <= 0) {
            throw new IllegalStateException(
                    "Operation leaseDuration ("
                            + operation.leaseDuration()
                            + ") must exceed providerTimeout ("
                            + operation.providerTimeout()
                            + ") + statementTimeout ("
                            + operation.statementTimeout()
                            + ")");
        }
    }

    public String getOrderServiceUrl() {
        return orderServiceUrl;
    }

    public boolean isByPassCircuitBreaker() {
        return byPassCircuitBreaker;
    }

    public Cors getCors() {
        return cors;
    }

    public Operation getOperation() {
        return operation;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public Reconciler getReconciler() {
        return reconciler;
    }

    public record Cors(
            String pathPattern,
            String allowedMethods,
            String allowedHeaders,
            String allowedOriginPatterns,
            boolean allowCredentials) {

        @ConstructorBinding
        public Cors(
                @DefaultValue("/api/**") String pathPattern,
                @DefaultValue("*") String allowedMethods,
                @DefaultValue("*") String allowedHeaders,
                @DefaultValue("*") String allowedOriginPatterns,
                @DefaultValue("true") boolean allowCredentials) {
            this.pathPattern = pathPattern != null ? pathPattern : "/api/**";
            this.allowedMethods = allowedMethods != null ? allowedMethods : "*";
            this.allowedHeaders = allowedHeaders != null ? allowedHeaders : "*";
            this.allowedOriginPatterns =
                    allowedOriginPatterns != null ? allowedOriginPatterns : "*";
            this.allowCredentials = allowCredentials;
        }

        public Cors() {
            this("/api/**", "*", "*", "*", true);
        }

        public String getPathPattern() {
            return pathPattern;
        }

        public String getAllowedMethods() {
            return allowedMethods;
        }

        public String getAllowedHeaders() {
            return allowedHeaders;
        }

        public String getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }
    }

    public record Operation(
            Duration leaseDuration,
            Duration providerTimeout,
            Duration statementTimeout,
            Duration lockTimeout,
            int maxAttempts) {

        @ConstructorBinding
        public Operation(
                @DefaultValue("10s") Duration leaseDuration,
                @DefaultValue("3s") Duration providerTimeout,
                @DefaultValue("2s") Duration statementTimeout,
                @DefaultValue("2s") Duration lockTimeout,
                @DefaultValue("3") int maxAttempts) {
            this.leaseDuration = leaseDuration != null ? leaseDuration : Duration.ofSeconds(10);
            this.providerTimeout =
                    providerTimeout != null ? providerTimeout : Duration.ofSeconds(3);
            this.statementTimeout =
                    statementTimeout != null ? statementTimeout : Duration.ofSeconds(2);
            this.lockTimeout = lockTimeout != null ? lockTimeout : Duration.ofSeconds(2);
            this.maxAttempts = maxAttempts;
        }

        public Operation() {
            this(
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(2),
                    3);
        }

        public Duration getLeaseDuration() {
            return leaseDuration;
        }

        public Duration getProviderTimeout() {
            return providerTimeout;
        }

        public Duration getStatementTimeout() {
            return statementTimeout;
        }

        public Duration getLockTimeout() {
            return lockTimeout;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }
    }

    public record Outbox(
            boolean enabled,
            Duration publishDelay,
            Duration lockTimeout,
            int maxRetries,
            int batchSize,
            Duration sendTimeout) {

        @ConstructorBinding
        public Outbox(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("500ms") Duration publishDelay,
                @DefaultValue("30s") Duration lockTimeout,
                @DefaultValue("3") int maxRetries,
                @DefaultValue("100") int batchSize,
                @DefaultValue("5s") Duration sendTimeout) {
            this.enabled = enabled;
            this.publishDelay = publishDelay != null ? publishDelay : Duration.ofMillis(500);
            this.lockTimeout = lockTimeout != null ? lockTimeout : Duration.ofSeconds(30);
            this.maxRetries = maxRetries;
            this.batchSize = batchSize;
            this.sendTimeout = sendTimeout != null ? sendTimeout : Duration.ofSeconds(5);
        }

        public Outbox() {
            this(
                    true,
                    Duration.ofMillis(500),
                    Duration.ofSeconds(30),
                    3,
                    100,
                    Duration.ofSeconds(5));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public Duration getPublishDelay() {
            return publishDelay;
        }

        public Duration getLockTimeout() {
            return lockTimeout;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public Duration getSendTimeout() {
            return sendTimeout;
        }
    }

    public record Reconciler(
            boolean enabled,
            Duration delay,
            int batchSize,
            Duration initialBackoff,
            Duration maxBackoff) {

        @ConstructorBinding
        public Reconciler(
                @DefaultValue("true") boolean enabled,
                @DefaultValue("1s") Duration delay,
                @DefaultValue("50") int batchSize,
                @DefaultValue("1s") Duration initialBackoff,
                @DefaultValue("10s") Duration maxBackoff) {
            this.enabled = enabled;
            this.delay = delay != null ? delay : Duration.ofSeconds(1);
            this.batchSize = batchSize;
            this.initialBackoff = initialBackoff != null ? initialBackoff : Duration.ofSeconds(1);
            this.maxBackoff = maxBackoff != null ? maxBackoff : Duration.ofSeconds(10);
        }

        public Reconciler() {
            this(true, Duration.ofSeconds(1), 50, Duration.ofSeconds(1), Duration.ofSeconds(10));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public Duration getDelay() {
            return delay;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }
    }
}
