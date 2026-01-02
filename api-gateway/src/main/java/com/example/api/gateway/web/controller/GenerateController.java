/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.controller;

import com.example.api.gateway.model.GenerationResponse;
import com.example.api.gateway.model.ServiceResult;
import com.example.api.gateway.model.ServiceType;
import com.example.api.gateway.web.api.GenerateAPI;
import com.example.apigateway.util.LogSanitizer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/** Controller that orchestrates data generation calls to microservices. */
@RestController
@RequestMapping("/api/generate")
public class GenerateController implements GenerateAPI {

    private static final Logger logger = LoggerFactory.getLogger(GenerateController.class);

    private static final String CATALOG_SERVICE_URL =
            "lb://CATALOG-SERVICE/catalog-service/api/catalog/generate";
    private static final String INVENTORY_SERVICE_URL =
            "lb://INVENTORY-SERVICE/inventory-service/api/inventory/generate";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(500);

    private final WebClient webClient;
    private final Duration delayBetweenServices;
    private final boolean exposeUpstreamErrors;

    public GenerateController(
            @LoadBalanced WebClient.Builder webClientBuilder,
            @Value("${gateway.delay-between-services:5s}") Duration delayBetweenServices,
            @Value("${gateway.expose-upstream-errors:false}") boolean exposeUpstreamErrors) {
        this.webClient = webClientBuilder.build();
        this.delayBetweenServices = delayBetweenServices;
        this.exposeUpstreamErrors = exposeUpstreamErrors;
    }

    /**
     * Endpoint that orchestrates data generation across services: 1. First calls catalog-service 2.
     * If catalog-service returns HTTP 200, waits for specified delay 3. Then calls
     * inventory-service 4. Returns appropriate response based on service call results
     *
     * @return Mono with response message containing results from both services
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Override
    public Mono<@NonNull ResponseEntity<@NonNull GenerationResponse>> generate() {
        return callMicroservice(CATALOG_SERVICE_URL, ServiceType.CATALOG)
                .flatMap(
                        catalogResult -> {
                            if (catalogResult.status() == HttpStatus.OK.value()) {
                                // Only call inventory if catalog succeeded with status 200
                                return Mono.just(catalogResult)
                                        .delayElement(this.delayBetweenServices)
                                        .flatMap(
                                                catalogData ->
                                                        callMicroservice(
                                                                        INVENTORY_SERVICE_URL,
                                                                        ServiceType.INVENTORY)
                                                                .map(
                                                                        inventoryResult ->
                                                                                createResponseEntity(
                                                                                        catalogData,
                                                                                        inventoryResult)));
                            } else {
                                // Don't call inventory service if catalog failed
                                return Mono.just(
                                        ResponseEntity.status(catalogResult.status())
                                                .body(
                                                        new GenerationResponse(
                                                                "error",
                                                                "Error generating data in catalog service",
                                                                Map.of(
                                                                        "catalog",
                                                                        catalogResult
                                                                                .response()))));
                            }
                        })
                .onErrorResume(this::handleGenerationError);
    }

    /**
     * Creates an appropriate response entity based on the result of the inventory service call.
     *
     * @param catalogData The result from the catalog service call
     * @param inventoryResult The result from the inventory service call
     * @return ResponseEntity with appropriate status and body
     */
    private ResponseEntity<GenerationResponse> createResponseEntity(
            ServiceResult catalogData, ServiceResult inventoryResult) {
        if (inventoryResult.status() == HttpStatus.OK.value()) {
            return ResponseEntity.ok(
                    new GenerationResponse(
                            "success",
                            "Generation process completed successfully",
                            Map.of(
                                    "catalog", catalogData.response(),
                                    "inventory", inventoryResult.response())));
        } else {
            return ResponseEntity.status(inventoryResult.status())
                    .body(
                            new GenerationResponse(
                                    "error",
                                    "Error generating data in inventory service",
                                    Map.of(
                                            "catalog", catalogData.response(),
                                            "inventory", inventoryResult.response())));
        }
    }

    /**
     * Makes a call to a microservice with retry logic and timeout handling.
     *
     * @param url The URL of the microservice to call
     * @param serviceType The type of service being called (for error messages)
     * @return Mono containing the service result
     */
    private Mono<ServiceResult> callMicroservice(String url, ServiceType serviceType) {
        return webClient
                .get()
                .uri(url)
                .retrieve()
                .toEntity(String.class) // Original Mono<ResponseEntity<String>>
                .timeout(REQUEST_TIMEOUT) // Apply timeout to each attempt
                .map(this::toServiceResult)
                .retryWhen(createRetrySpec(url))
                .onErrorResume(throwable -> handleCallError(throwable, url, serviceType));
    }

    private Retry createRetrySpec(String url) {
        return Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_BACKOFF)
                .jitter(0.5) // Add jitter to avoid thundering herd
                .filter(throwable -> shouldRetry(throwable, url))
                .onRetryExhaustedThrow(
                        (retryBackoffSpec, retrySignal) -> {
                            logger.warn(
                                    "Retries exhausted for {} after {} attempts. Propagating last error: {}",
                                    url,
                                    retrySignal.totalRetries(),
                                    LogSanitizer.sanitizeForLog(retrySignal.failure().toString()));
                            return retrySignal.failure();
                        });
    }

    private ServiceResult toServiceResult(ResponseEntity<String> response) {
        return new ServiceResult(response.getStatusCode().value(), response.getBody());
    }

    private boolean shouldRetry(Throwable throwable, String url) {
        if (isTimeout(throwable)) {
            logger.debug(
                    "Retry filter: TimeoutException for {}, not retrying this attempt, but retry mechanism may try again if not exhausted.",
                    url);
            return false;
        }
        if (throwable instanceof WebClientResponseException wce) {
            logger.debug(
                    "Retry filter: WebClientResponseException status {} for {}, message: '{}'",
                    wce.getStatusCode(),
                    url,
                    LogSanitizer.sanitizeForLog(wce.getMessage()));

            if (wce.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE
                    || wce.getStatusCode() == HttpStatus.BAD_GATEWAY
                    || wce.getStatusCode() == HttpStatus.GATEWAY_TIMEOUT
                    || wce.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                logger.debug("Retry filter: {} for {}, retrying.", wce.getStatusCode(), url);
                return true;
            }

            String wceMessage = safeLower(LogSanitizer.sanitizeForLog(wce.getMessage()));
            String wceBody = safeLower(LogSanitizer.sanitizeForLog(safeLowerResponseBody(wce)));

            boolean shouldRetry =
                    wceBody.contains("connection refused")
                            || wceMessage.contains("transient")
                            || wceMessage.contains("connection refused");

            logger.debug(
                    "Retry filter: WCE (status {}) for {} - Retrying: {}",
                    wce.getStatusCode(),
                    url,
                    shouldRetry);

            return shouldRetry;
        }

        String message =
                throwable.getMessage() != null
                        ? LogSanitizer.sanitizeForLog(throwable.getMessage())
                                .toLowerCase(Locale.ROOT)
                        : "";
        boolean retry = message.contains("transient") || message.contains("connection refused");
        logger.debug(
                "Retry filter: Other throwable ({}) for {} - message: '{}', Retrying: {}",
                throwable.getClass().getSimpleName(),
                url,
                message,
                retry);
        return retry;
    }

    private String safeLowerResponseBody(WebClientResponseException wce) {
        try {
            String rawBody = wce.getResponseBodyAsString();
            return safeLower(rawBody);
        } catch (Exception ex) {
            logger.warn(
                    "Could not get response body for WCE in retry filter: {}",
                    LogSanitizer.sanitizeException(ex));
            return "";
        }
    }

    private String safeLower(String s) {
        return s != null ? s.toLowerCase(Locale.ROOT) : "";
    }

    private Mono<ServiceResult> handleCallError(
            Throwable throwable, String url, ServiceType serviceType) {
        logger.warn(
                "Error calling {} service at URL {}: {}",
                serviceType.getId(),
                url,
                LogSanitizer.sanitizeForLog(throwable.getMessage()));

        if (isTimeout(throwable)) {
            return Mono.just(
                    new ServiceResult(HttpStatus.REQUEST_TIMEOUT.value(), "Timeout occurred"));
        }

        if (throwable instanceof WebClientResponseException wce) {
            HttpStatus status = resolveStatusOrDefault(wce);
            String detailMessage = extractWceDetail(wce);

            String errorMessage = getErrorMessage(serviceType, wce, detailMessage);
            return Mono.just(new ServiceResult(status.value(), errorMessage));
        }

        return Mono.just(
                new ServiceResult(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Unexpected error calling %s service: %s"
                                .formatted(
                                        serviceType.getId(),
                                        LogSanitizer.sanitizeForLog(throwable.getMessage()))));
    }

    private static String getErrorMessage(
            ServiceType serviceType, WebClientResponseException wce, String detailMessage) {
        String errorMessage;
        if (wce.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
            errorMessage = "Service temporarily unavailable";
        } else {
            // Reverted diagnostic change in message format
            errorMessage =
                    "Error from %s service: %s"
                            .formatted(serviceType.getId(), detailMessage.trim());
        }
        return errorMessage;
    }

    private ServiceType detectFailedService(Throwable e) {
        String exceptionMessage =
                e.getMessage() != null
                        ? LogSanitizer.sanitizeForLog(e.getMessage()).toLowerCase(Locale.ROOT)
                        : "";
        if (exceptionMessage.contains(ServiceType.CATALOG.getId())) {
            return ServiceType.CATALOG;
        }
        if (exceptionMessage.contains(ServiceType.INVENTORY.getId())) {
            return ServiceType.INVENTORY;
        }
        return null;
    }

    private boolean isTimeout(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException) return true;
        }
        return false;
    }

    private void putIfKnown(Map<String, String> map, ServiceType service, String value) {
        if (service != null) {
            map.put(service.getId(), value);
        }
    }

    private HttpStatus resolveStatusOrDefault(WebClientResponseException wce) {
        HttpStatus status = HttpStatus.resolve(wce.getStatusCode().value());
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String extractWceDetail(WebClientResponseException wce) {
        String wceResponseBody = wce.getResponseBodyAsString();
        if (!wceResponseBody.isEmpty()) {
            return wceResponseBody;
        }
        String statusText = wce.getStatusText();
        HttpStatus resolvedWceStatus = HttpStatus.resolve(wce.getStatusCode().value());
        if (statusText.trim().isEmpty()) {
            statusText = (resolvedWceStatus != null) ? resolvedWceStatus.getReasonPhrase() : "";
        }
        statusText = statusText.trim();
        return wce.getStatusCode().value() + (!statusText.isEmpty() ? " " + statusText : "");
    }

    /**
     * Handles errors that occur during the generation process.
     *
     * @param e The exception that occurred
     * @return Mono with an appropriate error response
     */
    private Mono<ResponseEntity<GenerationResponse>> handleGenerationError(Throwable e) {
        logger.error("Error in generation process: {}", LogSanitizer.sanitizeException(e), e);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorMessage = "An unexpected error occurred during data generation.";
        Map<String, String> serviceResponsesMap = new HashMap<>();
        ServiceType failedService = detectFailedService(e);

        if (isTimeout(e)) {
            status = HttpStatus.REQUEST_TIMEOUT;
            errorMessage = "Timeout occurred during data generation.";
            putIfKnown(serviceResponsesMap, failedService, "Timeout occurred");
        } else if (e instanceof WebClientResponseException wce) {
            status = resolveStatusOrDefault(wce);
            String specificErrorMessage = LogSanitizer.sanitizeForLog(extractWceDetail(wce));

            // Always log the detailed upstream message at debug level (or info for
            // visibility)
            String requestUri = "";
            try {
                var req = wce.getRequest();
                if (req != null && req.getURI() != null) {
                    requestUri = req.getURI().toString();
                }
            } catch (Exception ex) {
                // Swallow here; we're just trying to capture a best-effort URI for logging
            }
            logger.debug(
                    "Upstream error detail for URL/service: {}, detectedService={}, detail={}",
                    requestUri,
                    failedService,
                    specificErrorMessage);

            if (status == HttpStatus.SERVICE_UNAVAILABLE) {
                errorMessage = "Service temporarily unavailable.";
                putIfKnown(serviceResponsesMap, failedService, "Service temporarily unavailable");
            } else if (failedService != null) {
                // Use a generic client-facing message by default
                errorMessage =
                        "Error generating data in %s service".formatted(failedService.getId());

                if (exposeUpstreamErrors) {
                    String serviceSpecificDetail =
                            "Error from %s service: %s"
                                    .formatted(failedService.getId(), specificErrorMessage.trim());
                    serviceResponsesMap.put(failedService.getId(), serviceSpecificDetail);
                } else {
                    // Store only a generic marker in serviceResponsesMap to avoid leaking details
                    serviceResponsesMap.put(
                            failedService.getId(), "Upstream service error (hidden)");
                }
            } else {
                // Unknown failed service: don't include upstream body in client message by
                // default
                if (exposeUpstreamErrors) {
                    errorMessage =
                            "Error from unknown service: %s".formatted(specificErrorMessage.trim());
                } else {
                    errorMessage = "Error from upstream service";
                }
            }
        } else {
            putIfKnown(
                    serviceResponsesMap,
                    detectFailedService(e),
                    LogSanitizer.sanitizeForLog(e.getMessage()));
        }

        if ("An unexpected error occurred during data generation.".equals(errorMessage)
                && failedService != null) {
            errorMessage = "Error generating data in %s service".formatted(failedService.getId());
        }

        return Mono.just(
                ResponseEntity.status(status)
                        .body(new GenerationResponse("error", errorMessage, serviceResponsesMap)));
    }
}
