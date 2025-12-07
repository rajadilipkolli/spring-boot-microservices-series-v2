/***
<p>
    Licensed under MIT License Copyright (c) 2022-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.exception;

import jakarta.persistence.EntityNotFoundException;
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
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleOrderNotFound(
            OrderNotFoundException ex, WebRequest request) {
        log.warn("Order not found: {}", ex.getMessage());

        // Use the ProblemDetail from the exception itself since it already has proper formatting
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Order Not Found");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/not-found"));
        problemDetail.setProperty("errorCategory", "Generic");
        problemDetail.setProperty("timestamp", Instant.now());

        addCorrelationId(problemDetail, request);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleProductNotFound(
            ProductNotFoundException ex, WebRequest request) {
        log.warn("Product not found: {}", ex.getMessage());

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Product Not Found");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/not-found"));
        problemDetail.setProperty("errorCategory", "Generic");
        problemDetail.setProperty("timestamp", Instant.now());

        addCorrelationId(problemDetail, request);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleEntityNotFound(
            EntityNotFoundException ex, WebRequest request) {
        log.warn("Entity not found: {}", ex.getMessage());

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Resource Not Found");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/not-found"));
        problemDetail.setProperty("timestamp", Instant.now());
        addCorrelationId(problemDetail, request);

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {
        log.warn("Validation failed: {}", ex.getMessage());

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
        addCorrelationId(problemDetail, request);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleConstraintViolation(
            ConstraintViolationException ex, WebRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Constraint Violation");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/validation-error"));
        problemDetail.setProperty("timestamp", Instant.now());
        addCorrelationId(problemDetail, request);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problemDetail.setTitle("Invalid Request");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/invalid-request"));
        problemDetail.setProperty("timestamp", Instant.now());
        addCorrelationId(problemDetail, request);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<@NonNull ProblemDetail> handleGenericException(
            Exception ex, WebRequest request) {
        log.error("Unexpected error occurred: ", ex);

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setProperty("timestamp", Instant.now());
        addCorrelationId(problemDetail, request);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    private void addCorrelationId(ProblemDetail problemDetail, WebRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId != null) {
            problemDetail.setProperty("correlationId", correlationId);
        }
    }

    record ApiValidationError(String object, String field, Object rejectedValue, String message) {}
}
