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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
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

    public GenerateController(
            @LoadBalanced WebClient.Builder webClientBuilder,
            @Value("${gateway.delay-between-services:5s}") Duration delayBetweenServices) {
        this.webClient = webClientBuilder.build();
        this.delayBetweenServices = delayBetweenServices;
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
    public Mono<ResponseEntity<GenerationResponse>> generate() {
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
                .map(
                        response ->
                                new ServiceResult(
                                        response.getStatusCode().value(), response.getBody()))
                .retryWhen(
                        Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_BACKOFF)
                                .filter(
                                        throwable -> {
                                            if (throwable
                                                            instanceof
                                                            java.util.concurrent.TimeoutException
                                                    || throwable.getCause()
                                                            instanceof
                                                            java.util.concurrent.TimeoutException) {
                                                logger.debug(
                                                        "Retry filter: TimeoutException for {}, not retrying this attempt, but retry mechanism may try again if not exhausted.",
                                                        url);
                                                return false;
                                            }
                                            if (throwable
                                                    instanceof WebClientResponseException wce) {
                                                logger.debug(
                                                        "Retry filter: WebClientResponseException status {} for {}, message: '{}'",
                                                        wce.getStatusCode(),
                                                        url,
                                                        wce.getMessage());

                                                if (wce.getStatusCode()
                                                        == HttpStatus.SERVICE_UNAVAILABLE) {
                                                    logger.debug(
                                                            "Retry filter: SERVICE_UNAVAILABLE for {}, retrying.",
                                                            url);
                                                    return true; // Explicitly retry 503
                                                }

                                                wce.getMessage();
                                                String wceMessage = wce.getMessage().toLowerCase();
                                                String wceBody = "";
                                                try {
                                                    String rawBody = wce.getResponseBodyAsString();
                                                    wceBody = rawBody.toLowerCase();
                                                } catch (Exception ex) {
                                                    logger.warn(
                                                            "Could not get response body for WCE in retry filter for url {}: {}",
                                                            url,
                                                            ex.getMessage());
                                                }

                                                boolean bodyContainsConnectionRefused =
                                                        wceBody.contains("connection refused");
                                                boolean messageContainsTransient =
                                                        wceMessage.contains("transient");
                                                boolean messageContainsConnectionRefused =
                                                        wceMessage.contains("connection refused");

                                                boolean shouldRetry =
                                                        bodyContainsConnectionRefused
                                                                || messageContainsTransient
                                                                || messageContainsConnectionRefused;

                                                logger.debug(
                                                        "Retry filter: WCE (status {}) for {} - BodyCR: {}, MsgTransient: {}, MsgCR: {}. Retrying: {}",
                                                        wce.getStatusCode(),
                                                        url,
                                                        bodyContainsConnectionRefused,
                                                        messageContainsTransient,
                                                        messageContainsConnectionRefused,
                                                        shouldRetry);

                                                return shouldRetry;
                                            }
                                            String message =
                                                    throwable.getMessage() != null
                                                            ? throwable.getMessage().toLowerCase()
                                                            : "";
                                            boolean retry =
                                                    message.contains("transient")
                                                            || message.contains(
                                                                    "connection refused");
                                            logger.debug(
                                                    "Retry filter: Other throwable ({}) for {} - message: '{}', Retrying: {}",
                                                    throwable.getClass().getSimpleName(),
                                                    url,
                                                    message,
                                                    retry);
                                            return retry;
                                        })
                                .onRetryExhaustedThrow(
                                        (retryBackoffSpec, retrySignal) -> {
                                            logger.warn(
                                                    "Retries exhausted for {} after {} attempts. Propagating last error: {}",
                                                    url,
                                                    retrySignal.totalRetries(),
                                                    retrySignal.failure().toString());
                                            return retrySignal.failure();
                                        }))
                .onErrorResume(
                        throwable -> {
                            logger.warn(
                                    "Error calling {} service at URL {}: {}",
                                    serviceType.getId(),
                                    url,
                                    throwable.getMessage());
                            if (throwable instanceof java.util.concurrent.TimeoutException
                                    || throwable.getCause()
                                            instanceof java.util.concurrent.TimeoutException) {
                                return Mono.just(
                                        new ServiceResult(
                                                HttpStatus.REQUEST_TIMEOUT.value(),
                                                "Timeout occurred"));
                            }
                            if (throwable instanceof WebClientResponseException wce) {
                                HttpStatus status = HttpStatus.resolve(wce.getStatusCode().value());
                                if (status == null) {
                                    status = HttpStatus.INTERNAL_SERVER_ERROR;
                                }
                                String responseBody = wce.getResponseBodyAsString();
                                String detailMessage;
                                if (!responseBody.isEmpty()) {
                                    detailMessage = responseBody;
                                } else {
                                    // Body is empty, construct message from status code and status
                                    // text
                                    String statusText = wce.getStatusText(); // e.g., "Bad Gateway"
                                    HttpStatus resolvedWceStatus =
                                            HttpStatus.resolve(wce.getStatusCode().value());
                                    if (statusText.trim().isEmpty()) {
                                        statusText =
                                                (resolvedWceStatus != null)
                                                        ? resolvedWceStatus.getReasonPhrase()
                                                        : "";
                                    }
                                    statusText = statusText.trim();
                                    detailMessage =
                                            wce.getStatusCode().value()
                                                    + (!statusText.isEmpty()
                                                            ? " " + statusText
                                                            : "");
                                }

                                String errorMessage =
                                        getErrorMessage(serviceType, wce, detailMessage);
                                return Mono.just(new ServiceResult(status.value(), errorMessage));
                            }
                            // Fallback for other types of errors
                            return Mono.just(
                                    new ServiceResult(
                                            HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                            String.format(
                                                    "Unexpected error calling %s service: %s",
                                                    serviceType.getId(), throwable.getMessage())));
                        });
    }

    private static String getErrorMessage(
            ServiceType serviceType, WebClientResponseException wce, String detailMessage) {
        String errorMessage;
        if (wce.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
            errorMessage = "Service temporarily unavailable";
        } else {
            // Reverted diagnostic change in message format
            errorMessage =
                    String.format(
                            "Error from %s service: %s", serviceType.getId(), detailMessage.trim());
        }
        return errorMessage;
    }

    /**
     * Handles errors that occur during the generation process.
     *
     * @param e The exception that occurred
     * @return Mono with an appropriate error response
     */
    private Mono<ResponseEntity<GenerationResponse>> handleGenerationError(Throwable e) {
        logger.error("Error in generation process: {}", e.getMessage(), e);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String errorMessage = "An unexpected error occurred during data generation.";
        Map<String, String> serviceResponsesMap = new HashMap<>();

        // Determine which service might have failed based on the exception message
        // This is a heuristic and might need refinement
        String exceptionMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        ServiceType failedService = null;
        if (exceptionMessage.contains(ServiceType.CATALOG.getId())) {
            failedService = ServiceType.CATALOG;
        } else if (exceptionMessage.contains(ServiceType.INVENTORY.getId())) {
            failedService = ServiceType.INVENTORY;
        }

        if (e instanceof java.util.concurrent.TimeoutException
                || e.getCause() instanceof java.util.concurrent.TimeoutException) {
            status = HttpStatus.REQUEST_TIMEOUT;
            errorMessage = "Timeout occurred during data generation.";
            if (failedService != null) {
                serviceResponsesMap.put(failedService.getId(), "Timeout occurred");
            }
        } else if (e instanceof WebClientResponseException wce) {
            status = HttpStatus.resolve(wce.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR; // default if status code is unknown
            }
            String wceResponseBody = wce.getResponseBodyAsString();
            String specificErrorMessage;
            if (!wceResponseBody.isEmpty()) {
                specificErrorMessage = wceResponseBody;
            } else {
                String statusText = wce.getStatusText();
                HttpStatus resolvedWceStatus = HttpStatus.resolve(wce.getStatusCode().value());
                if (statusText.trim().isEmpty()) {
                    statusText =
                            (resolvedWceStatus != null) ? resolvedWceStatus.getReasonPhrase() : "";
                }
                statusText = statusText.trim();
                specificErrorMessage =
                        wce.getStatusCode().value()
                                + (!statusText.isEmpty() ? " " + statusText : "");
            }

            if (status == HttpStatus.SERVICE_UNAVAILABLE) {
                errorMessage = "Service temporarily unavailable.";
                if (failedService != null) {
                    serviceResponsesMap.put(
                            failedService.getId(), "Service temporarily unavailable");
                }
            } else {
                if (failedService != null) {
                    errorMessage =
                            String.format(
                                    "Error generating data in %s service", failedService.getId());
                    String serviceSpecificDetail =
                            String.format(
                                    "Error from %s service: %s",
                                    failedService.getId(), specificErrorMessage.trim());
                    serviceResponsesMap.put(failedService.getId(), serviceSpecificDetail);
                } else {
                    errorMessage =
                            String.format(
                                    "Error from unknown service: %s", specificErrorMessage.trim());
                    // If failedService is null, we don't know which key to use in
                    // serviceResponsesMap
                }
            }
        } else {
            // For other exceptions, keep generic error message
            if (failedService != null) {
                serviceResponsesMap.put(failedService.getId(), e.getMessage());
            }
        }

        // Ensure a general error message if it's still the default one and we identified a service
        if ("An unexpected error occurred during data generation.".equals(errorMessage)
                && failedService != null) {
            errorMessage =
                    String.format("Error generating data in %s service", failedService.getId());
        }

        return Mono.just(
                ResponseEntity.status(status)
                        .body(new GenerationResponse("error", errorMessage, serviceResponsesMap)));
    }
}
