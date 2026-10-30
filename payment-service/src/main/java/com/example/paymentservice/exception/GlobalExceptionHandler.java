/*** Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli. ***/
package com.example.paymentservice.exception;

import com.example.common.logging.LogSanitizer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpServerErrorException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleCustomerNotFound(
            CustomerNotFoundException ex) {
        log.warn("Customer not found: {}", LogSanitizer.sanitizeException(ex));

        // Use the ProblemDetail from the exception itself since it already has proper formatting
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.NOT_FOUND, LogSanitizer.sanitizeException(ex));
        problemDetail.setTitle("Customer Not Found");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/not-found"));
        problemDetail.setProperty("errorCategory", "Generic");
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    /**
     * Returns HTTP 409 problem details for a duplicate customer.
     *
     * @param ex the duplicate-customer failure
     * @return the conflict response with a timestamp and sanitized error details
     */
    @ExceptionHandler(CustomerExistsException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleCustomerExists(CustomerExistsException ex) {
        log.warn("Customer already exists: {}", LogSanitizer.sanitizeException(ex));

        // Use the ProblemDetail from the exception itself since it already has proper formatting
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT, LogSanitizer.sanitizeException(ex));
        problemDetail.setTitle("Customer Already Exists");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/conflict"));
        problemDetail.setProperty("errorCategory", "Generic");
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", LogSanitizer.sanitizeException(ex));

        List<ApiValidationError> validationErrorsList =
                ex.getAllErrors().stream()
                        .map(
                                objectError -> {
                                    FieldError fieldError = (FieldError) objectError;
                                    return new ApiValidationError(
                                            fieldError.getObjectName(),
                                            fieldError.getField(),
                                            fieldError.getRejectedValue(),
                                            Objects.requireNonNull(fieldError.getDefaultMessage()));
                                })
                        .sorted(Comparator.comparing(ApiValidationError::field))
                        .toList();

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST, "Invalid request content.");
        problemDetail.setTitle("Constraint Violation");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/validation-error"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("violations", validationErrorsList);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex) {
        log.warn("Constraint violation: {}", LogSanitizer.sanitizeException(ex));

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST, LogSanitizer.sanitizeException(ex));
        problemDetail.setTitle("Constraint Violation");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/validation-error"));
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleIllegalArgument(
            IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", LogSanitizer.sanitizeException(ex));

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST, LogSanitizer.sanitizeException(ex));
        problemDetail.setTitle("Invalid Request");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/invalid-request"));
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    /**
     * Converts an upstream HTTP server error to an HTTP 503 problem response with a generic order
     * service unavailable message, and a timestamp.
     */
    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleOrderServiceError(
            HttpServerErrorException ex) {
        log.warn("Order service error: {}", LogSanitizer.sanitizeException(ex));

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The order service is currently unavailable.");
        problemDetail.setTitle("Service Unavailable");
        problemDetail.setType(
                URI.create("https://api.microservices.com/errors/service-unavailable"));
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problemDetail);
    }

    /**
     * Converts a rejected circuit breaker call to an HTTP 503 problem response reporting an open
     * order service circuit, with a timestamp.
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleCircuitBreakerOpen(
            CallNotPermittedException ex) {
        log.warn(
                "Circuit breaker prevented access to order service: {}",
                LogSanitizer.sanitizeException(ex));

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The order service circuit breaker is currently open.");
        problemDetail.setTitle("Service Unavailable");
        problemDetail.setType(
                URI.create("https://api.microservices.com/errors/service-unavailable"));
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: ", ex);

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    record ApiValidationError(String object, String field, Object rejectedValue, String message) {}
}
