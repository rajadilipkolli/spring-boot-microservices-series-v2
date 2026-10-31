/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.config;

import com.example.api.gateway.model.ServiceType;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Centralized configuration for API Gateway generation workflow. */
@Validated
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    /** Delay between successful downstream service calls. */
    @NotNull private Duration delayBetweenServices = Duration.ofSeconds(5);

    /** Timeout applied to each downstream HTTP attempt. */
    @NotNull private Duration requestTimeout = Duration.ofSeconds(10);

    /** Downstream service URLs. */
    private Map<ServiceType, String> serviceUrls = new EnumMap<>(ServiceType.class);

    private Duration connectionTimeout = Duration.ofSeconds(2);

    private Duration responseTimeout = Duration.ofSeconds(8);

    private Duration pendingAcquireTimeout = Duration.ofSeconds(2);

    private Duration maxIdleTime = Duration.ofSeconds(30);

    private Duration maxLifeTime = Duration.ofMinutes(5);

    private Duration evictInBackground = Duration.ofSeconds(30);

    private int maxConnections = 100;

    private Duration workflowTimeout = Duration.ofSeconds(45);

    public Duration getWorkflowTimeout() {
        return workflowTimeout;
    }

    /** Sets the workflow deadline; the duration must be nonnull and positive. */
    public void setWorkflowTimeout(Duration workflowTimeout) {
        if (workflowTimeout == null || workflowTimeout.isZero() || workflowTimeout.isNegative()) {
            throw new IllegalArgumentException("workflowTimeout must be positive");
        }

        this.workflowTimeout = workflowTimeout;
    }

    /**
     * Returns the configured HTTP or HTTPS URL for a downstream service.
     *
     * @throws NullPointerException if the service type is null
     * @throws IllegalStateException if the URL is missing, blank, or invalid
     */
    public String getServiceUrl(ServiceType serviceType) {

        Objects.requireNonNull(serviceType, "ServiceType must not be null");

        String url = serviceUrls.get(serviceType);

        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "Missing gateway URL configuration for service: " + serviceType.getId());
        }

        validateServiceUrl(serviceType, url);

        return url;
    }

    /** Validates downstream service URL. */
    private void validateServiceUrl(ServiceType serviceType, String url) {

        try {
            URI uri = URI.create(url);

            String scheme = uri.getScheme();

            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null) {

                throw new IllegalArgumentException("Invalid HTTP URL");
            }

        } catch (IllegalArgumentException exception) {

            throw new IllegalStateException(
                    "Invalid URL configured for service: " + serviceType.getId(), exception);
        }
    }

    public Duration getDelayBetweenServices() {
        return delayBetweenServices;
    }

    /**
     * Sets the delay between successful service calls; zero is allowed, but null and negative
     * values are not.
     */
    public void setDelayBetweenServices(Duration delayBetweenServices) {

        if (delayBetweenServices == null || delayBetweenServices.isNegative()) {

            throw new IllegalArgumentException("delayBetweenServices must be zero or positive");
        }

        this.delayBetweenServices = delayBetweenServices;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    /** Sets the timeout for each downstream attempt; the duration must be nonnull and positive. */
    public void setRequestTimeout(Duration requestTimeout) {

        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {

            throw new IllegalArgumentException("requestTimeout must be positive");
        }

        this.requestTimeout = requestTimeout;
    }

    /** Returns an unmodifiable copy of the configured service URLs. */
    public Map<ServiceType, String> getServiceUrls() {
        return Map.copyOf(serviceUrls);
    }

    /** Replaces the configured service URLs with a copy of the supplied map. */
    public void setServiceUrls(Map<ServiceType, String> serviceUrls) {

        Objects.requireNonNull(serviceUrls, "Service URLs must not be null");

        this.serviceUrls = new EnumMap<>(ServiceType.class);

        this.serviceUrls.putAll(serviceUrls);
    }

    public Duration getConnectionTimeout() {
        return connectionTimeout;
    }

    /** Sets the connection timeout; the duration must be nonnull and positive. */
    public void setConnectionTimeout(Duration connectionTimeout) {
        this.connectionTimeout = requirePositive(connectionTimeout, "connectionTimeout");
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    /** Sets the response timeout; the duration must be nonnull and positive. */
    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = requirePositive(responseTimeout, "responseTimeout");
    }

    public Duration getPendingAcquireTimeout() {
        return pendingAcquireTimeout;
    }

    /** Sets the connection acquisition timeout; the duration must be nonnull and positive. */
    public void setPendingAcquireTimeout(Duration pendingAcquireTimeout) {
        this.pendingAcquireTimeout =
                requirePositive(pendingAcquireTimeout, "pendingAcquireTimeout");
    }

    public Duration getMaxIdleTime() {
        return maxIdleTime;
    }

    /**
     * Sets the maximum idle time for pooled connections; the duration must be nonnull and positive.
     */
    public void setMaxIdleTime(Duration maxIdleTime) {
        this.maxIdleTime = requirePositive(maxIdleTime, "maxIdleTime");
    }

    public Duration getMaxLifeTime() {
        return maxLifeTime;
    }

    /**
     * Sets the maximum lifetime of pooled connections; the duration must be nonnull and positive.
     */
    public void setMaxLifeTime(Duration maxLifeTime) {
        this.maxLifeTime = requirePositive(maxLifeTime, "maxLifeTime");
    }

    public Duration getEvictInBackground() {
        return evictInBackground;
    }

    /** Sets the background eviction interval; the duration must be nonnull and positive. */
    public void setEvictInBackground(Duration evictInBackground) {
        this.evictInBackground = requirePositive(evictInBackground, "evictInBackground");
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    /** Sets the maximum number of pooled connections, which must be at least one. */
    public void setMaxConnections(int maxConnections) {
        if (maxConnections < 1) {
            throw new IllegalArgumentException("maxConnections must be greater than zero");
        }
        this.maxConnections = maxConnections;
    }

    private Duration requirePositive(Duration value, String property) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(property + " must be positive");
        }
        return value;
    }
}
