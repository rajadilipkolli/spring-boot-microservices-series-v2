/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.config;

import com.example.catalogservice.config.logging.LogWriter;
import com.example.catalogservice.config.logging.Loggable;
import com.example.catalogservice.config.logging.LoggingAspect;
import com.example.catalogservice.entities.Product;
import com.example.catalogservice.exception.CustomResponseStatusException;
import com.example.catalogservice.exception.ProductAlreadyExistsException;
import com.example.catalogservice.exception.ProductNotFoundException;
import com.example.catalogservice.mapper.ProductMapper;
import com.example.catalogservice.model.payload.ProductDto;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.InventoryResponse;
import com.example.catalogservice.model.response.PagedResult;
import com.example.catalogservice.model.response.ProductResponse;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

public class CatalogServiceRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Register reflection hints for R2DBC entity classes
        hints.reflection()
                .registerType(
                        Product.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);

        // Register reflection hints for DTO/record classes
        hints.reflection()
                .registerType(
                        ProductDto.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        ProductRequest.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        ProductResponse.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        InventoryResponse.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        PagedResult.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);

        // Register reflection hints for MapStruct
        hints.reflection()
                .registerType(
                        ProductMapper.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS)
                .registerType(
                        TypeReference.of("com.example.catalogservice.mapper.ProductMapperImpl"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS);

        // Register reflection hints for ConfigurationProperties
        hints.reflection()
                .registerType(
                        ApplicationProperties.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS)
                .registerType(
                        ApplicationProperties.Cors.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        ApplicationProperties.Resilience.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);

        // Register reflection hints for Resilience4j Properties
        hints.reflection()
                .registerType(
                        TypeReference.of(
                                "io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerProperties"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of(
                                "io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerProperties$InstanceProperties"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of(
                                "io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterProperties"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of(
                                "io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterProperties$InstanceProperties"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of(
                                "io.github.resilience4j.springboot3.retry.autoconfigure.RetryProperties"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of(
                                "io.github.resilience4j.springboot3.retry.autoconfigure.RetryProperties$InstanceProperties"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of(
                                "io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterProperties"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of(
                                "io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterProperties$InstanceProperties"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);

        // Register reflection hints for Resilience4j Aspects
        hints.reflection()
                .registerType(
                        TypeReference.of(
                                "io.github.resilience4j.spring6.circuitbreaker.configure.CircuitBreakerAspect"),
                        MemberCategory.INVOKE_DECLARED_METHODS)
                .registerType(
                        TypeReference.of(
                                "io.github.resilience4j.spring6.ratelimiter.configure.RateLimiterAspect"),
                        MemberCategory.INVOKE_DECLARED_METHODS)
                .registerType(
                        TypeReference.of(
                                "io.github.resilience4j.spring6.retry.configure.RetryAspect"),
                        MemberCategory.INVOKE_DECLARED_METHODS)
                .registerType(
                        TypeReference.of(
                                "io.github.resilience4j.spring6.timelimiter.configure.TimeLimiterAspect"),
                        MemberCategory.INVOKE_DECLARED_METHODS);

        // Register common Spring Cloud Hints
        hints.reflection()
                .registerType(
                        TypeReference.of(
                                "org.springframework.cloud.client.loadbalancer.LoadBalancerProperties"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of(
                                "org.springframework.cloud.client.loadbalancer.LoadBalancerProperties$StickySession"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);

        // Register reflection hints for custom logging
        hints.reflection()
                .registerType(
                        Loggable.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        LoggingAspect.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        LogWriter.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);

        // Register reflection hints for custom exceptions
        hints.reflection()
                .registerType(
                        ProductNotFoundException.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        ProductAlreadyExistsException.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        CustomResponseStatusException.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);

        // Register resource hints for Joda-Time timezone data
        hints.resources().registerPattern("org/joda/time/tz/data/**/*");

        // Register reflection hints for Joda-Time ZoneInfoProvider (used by Jackson)
        hints.reflection()
                .registerType(
                        TypeReference.of("org.joda.time.tz.ZoneInfoProvider"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);

        // Register reflection hints for Spring Cloud Config Client properties
        hints.reflection()
                .registerType(
                        TypeReference.of(
                                "org.springframework.cloud.config.client.ConfigClientProperties"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of(
                                "org.springframework.cloud.config.client.ConfigClientProperties$Credentials"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);

        // Register reflection hints for Spring Cloud Config model (needed for Jackson
        // deserialization)
        hints.reflection()
                .registerType(
                        TypeReference.of(
                                "org.springframework.cloud.config.environment.Environment"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of(
                                "org.springframework.cloud.config.environment.PropertySource"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);

        // Register reflection hints for PostgreSQL JDBC Driver (used by Liquibase)
        hints.reflection()
                .registerType(
                        TypeReference.of("org.postgresql.Driver"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of("org.postgresql.util.PSQLException"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        TypeReference.of("org.postgresql.PGProperty"),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);
    }
}
