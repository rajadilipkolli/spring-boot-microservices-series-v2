/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import com.example.catalogservice.config.logging.Loggable;
import com.example.catalogservice.exception.CustomResponseStatusException;
import com.example.catalogservice.model.response.InventoryDto;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Loggable
public class InventoryServiceProxy {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final String DEFAULT = "default";

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final RateLimiter rateLimiter;
    private final TimeLimiter timeLimiter;

    public InventoryServiceProxy(
            WebClient webClient,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            RateLimiterRegistry rateLimiterRegistry,
            TimeLimiterRegistry timeLimiterRegistry) {
        this.webClient = webClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(DEFAULT);
        this.retry = retryRegistry.retry("product-api");
        this.rateLimiter = rateLimiterRegistry.rateLimiter(DEFAULT);
        this.timeLimiter = timeLimiterRegistry.timeLimiter(DEFAULT);
    }

    public Mono<InventoryDto> getInventoryByProductCode(String productCode) {
        log.info("Fetching inventory information for productCode {}", productCode);
        return executeWithFallback(
                webClient
                        .get()
                        .uri("/api/inventory/{productCode}", productCode)
                        .retrieve()
                        .bodyToMono(InventoryDto.class),
                throwable -> getInventoryByProductCodeFallBack(productCode, throwable));
    }

    private Mono<InventoryDto> getInventoryByProductCodeFallBack(String code, Throwable e) {
        log.error("Exception occurred while fetching product details for code :{}", code, e);
        return Mono.just(new InventoryDto(code, 0));
    }

    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(
            name = "getInventoryByProductCodes",
            fallbackMethod = "getInventoryByProductCodesFallBack")
    public Flux<InventoryDto> getInventoryByProductCodes(List<String> productCodeList) {
        log.info("Fetching inventory information for productCodes : {}", productCodeList);
        return webClient
                .get()
                .uri(
                        uriBuilder -> {
                            uriBuilder.path("/api/inventory/product");
                            uriBuilder.queryParam("codes", productCodeList);
                            return uriBuilder.build();
                        })
                .retrieve()
                .bodyToFlux(InventoryDto.class);
    }

    private Flux<InventoryDto> getInventoryByProductCodesFallBack(Exception e) {
        log.error("Exception occurred while fetching product details", e);
        return Flux.empty();
    }

    private <T> Mono<T> executeWithFallback(
            Mono<T> publisher, Function<Throwable, Mono<T>> fallback) {
        return publisher
                .transform(TimeLimiterOperator.of(timeLimiter))
                .transform(RateLimiterOperator.of(rateLimiter))
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(TimeoutException.class, fallback)
                .onErrorResume(RequestNotPermitted.class, fallback)
                .onErrorResume(
                        CallNotPermittedException.class,
                        throwable -> {
                            log.error(
                                    "Circuit Breaker is in [{}]... Providing fallback response without calling the API",
                                    circuitBreaker.getState());
                            throw new CustomResponseStatusException(
                                    SERVICE_UNAVAILABLE, "API service is unavailable");
                        });
    }
}
