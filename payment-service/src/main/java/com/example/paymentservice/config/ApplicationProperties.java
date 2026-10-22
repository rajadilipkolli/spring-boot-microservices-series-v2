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

    /**
     * Creates application settings, replacing null nested settings with defaults and validating
     * that the operation lease exceeds the combined provider and statement timeouts.
     *
     * @throws IllegalStateException if the operation lease is too short
     */
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

    /** Creates default settings with no order service URL and circuit breaker bypass disabled. */
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

    /** Returns the base URL of the order service. */
    public String getOrderServiceUrl() {
        return orderServiceUrl;
    }

    /** Returns whether order lookups bypass the circuit breaker. */
    public boolean isByPassCircuitBreaker() {
        return byPassCircuitBreaker;
    }

    /** Returns the CORS settings. */
    public Cors getCors() {
        return cors;
    }

    /** Returns the payment operation settings. */
    public Operation getOperation() {
        return operation;
    }

    /** Returns the outbox publication settings. */
    public Outbox getOutbox() {
        return outbox;
    }

    /** Returns the reconciliation settings. */
    public Reconciler getReconciler() {
        return reconciler;
    }

    public record Cors(
            String pathPattern,
            String allowedMethods,
            String allowedHeaders,
            String allowedOriginPatterns,
            boolean allowCredentials) {

        /**
         * Creates CORS settings, using default patterns, methods, and headers for null arguments.
         */
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

        /**
         * Creates CORS settings for {@code /api/**} with wildcard methods, headers, and origins,
         * allowing credentials.
         */
        public Cors() {
            this("/api/**", "*", "*", "*", true);
        }

        /** Returns the URL pattern to which CORS settings apply. */
        public String getPathPattern() {
            return pathPattern;
        }

        /** Returns the allowed CORS methods. */
        public String getAllowedMethods() {
            return allowedMethods;
        }

        /** Returns the allowed CORS headers. */
        public String getAllowedHeaders() {
            return allowedHeaders;
        }

        /** Returns the allowed CORS origin patterns. */
        public String getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        /** Returns whether CORS requests may include credentials. */
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

        /** Creates operation settings, using default durations for null arguments. */
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

        /**
         * Creates operation settings with a 10-second lease, a 3-second provider timeout, 2-second
         * statement and lock timeouts, and a three-attempt limit.
         */
        public Operation() {
            this(
                    Duration.ofSeconds(10),
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(2),
                    Duration.ofSeconds(2),
                    3);
        }

        /** Returns the duration of an operation ownership lease. */
        public Duration getLeaseDuration() {
            return leaseDuration;
        }

        /** Returns the maximum wait for provider execution or lookup. */
        public Duration getProviderTimeout() {
            return providerTimeout;
        }

        /** Returns the PostgreSQL statement timeout for provider transactions. */
        public Duration getStatementTimeout() {
            return statementTimeout;
        }

        /** Returns the timeout for acquiring a PostgreSQL lock. */
        public Duration getLockTimeout() {
            return lockTimeout;
        }

        /** Returns the attempt limit checked before reconciliation re-executes an operation. */
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

        /** Creates outbox settings, using default durations for null arguments. */
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

        /**
         * Creates enabled outbox settings with a 500-millisecond publish delay, a 30-second lock
         * timeout, three retries, batches of 100, and a 5-second send timeout.
         */
        public Outbox() {
            this(
                    true,
                    Duration.ofMillis(500),
                    Duration.ofSeconds(30),
                    3,
                    100,
                    Duration.ofSeconds(5));
        }

        /** Returns whether scheduled outbox publication and reaping are enabled. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Returns the delay between scheduled outbox publication batches. */
        public Duration getPublishDelay() {
            return publishDelay;
        }

        /** Returns the age after which a processing outbox event can be reclaimed. */
        public Duration getLockTimeout() {
            return lockTimeout;
        }

        /** Returns the retry limit for outbox publication. */
        public int getMaxRetries() {
            return maxRetries;
        }

        /** Returns the maximum number of outbox events to claim per publication batch. */
        public int getBatchSize() {
            return batchSize;
        }

        /** Returns the maximum wait for a Kafka send to complete. */
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

        /** Creates reconciliation settings, using default durations for null arguments. */
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

        /**
         * Creates enabled reconciliation settings with a 1-second delay, batches of 50, and backoff
         * ranging from 1 to 10 seconds.
         */
        public Reconciler() {
            this(true, Duration.ofSeconds(1), 50, Duration.ofSeconds(1), Duration.ofSeconds(10));
        }

        /** Returns whether scheduled reconciliation is enabled. */
        public boolean isEnabled() {
            return enabled;
        }

        /** Returns the delay between scheduled reconciliation batches. */
        public Duration getDelay() {
            return delay;
        }

        /** Returns the maximum number of operations to claim per reconciliation batch. */
        public int getBatchSize() {
            return batchSize;
        }

        /** Returns the initial delay used when deferring reconciliation. */
        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        /** Returns the maximum reconciliation backoff duration. */
        public Duration getMaxBackoff() {
            return maxBackoff;
        }
    }
}
