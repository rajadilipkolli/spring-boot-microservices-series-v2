/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.exception;

import com.example.api.gateway.model.GenerationResponse;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceCallException.class)
    public Mono<ResponseEntity<GenerationResponse>> handleServiceCallException(
            ServiceCallException exception) {

        HttpStatus status = resolveStatus(exception);

        String service = exception.getServiceType().getId();

        LOGGER.error(
                "Downstream service failure. service={}, status={}",
                service,
                status.value(),
                exception);

        return Mono.just(
                ResponseEntity.status(status)
                        .body(
                                new GenerationResponse(
                                        "error",
                                        "Generation failed in " + service + " service.",
                                        Map.of(service, "Upstream service error"))));
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        ServerWebInputException.class,
        MethodValidationException.class
    })
    public Mono<ResponseEntity<GenerationResponse>> handleBadRequest(Exception exception) {

        LOGGER.warn("Invalid request: {}", exception.getMessage());

        return Mono.just(
                ResponseEntity.badRequest()
                        .body(
                                new GenerationResponse(
                                        "error", "Invalid generation request.", Map.of())));
    }

    @ExceptionHandler(WorkflowTimeoutException.class)
    public Mono<ResponseEntity<GenerationResponse>> handleWorkflowTimeout(
            WorkflowTimeoutException exception) {

        LOGGER.warn("Generation workflow deadline exceeded");

        return Mono.just(
                ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                        .body(
                                new GenerationResponse(
                                        "error",
                                        "Generation workflow exceeded the allowed execution time.",
                                        Map.of())));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<GenerationResponse>> handleUnexpectedException(Exception exception) {

        LOGGER.error("Unexpected API Gateway error", exception);

        return Mono.just(
                ResponseEntity.internalServerError()
                        .body(
                                new GenerationResponse(
                                        "error",
                                        "An unexpected error occurred during data generation.",
                                        Map.of())));
    }

    private HttpStatus resolveStatus(ServiceCallException exception) {

        if (hasTimeout(exception)) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }

        Integer upstreamStatus = exception.getUpstreamStatus();

        if (upstreamStatus != null) {
            if (upstreamStatus == 429) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }

            if (upstreamStatus >= 500) {
                return HttpStatus.BAD_GATEWAY;
            }
        }

        return HttpStatus.BAD_GATEWAY;
    }

    private boolean hasTimeout(Throwable exception) {

        for (Throwable current = exception; current != null; current = current.getCause()) {

            if (current instanceof TimeoutException) {
                return true;
            }
        }

        return false;
    }
}
