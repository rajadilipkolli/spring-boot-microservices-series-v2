/***
<p>
    Licensed under MIT License Copyright (c) 2022-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.exception;

import com.example.common.logging.LogSanitizer;
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

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Returns HTTP 404 problem details with the sanitized order error message. */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleOrderNotFound(OrderNotFoundException ex) {
        log.warn("Order not found: {}", LogSanitizer.sanitizeException(ex));

        // Use the ProblemDetail from the exception itself since it already has proper formatting
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.NOT_FOUND, LogSanitizer.sanitizeException(ex));
        problemDetail.setTitle("Order Not Found");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/not-found"));
        problemDetail.setProperty("errorCategory", "Generic");
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    /** Returns HTTP 404 problem details with the sanitized product error message. */
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleProductNotFound(
            ProductNotFoundException ex) {
        log.warn("Product not found: {}", LogSanitizer.sanitizeException(ex));

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.NOT_FOUND, LogSanitizer.sanitizeException(ex));
        problemDetail.setTitle("Product Not Found");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/not-found"));
        problemDetail.setProperty("errorCategory", "Generic");
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    /** Returns HTTP 404 problem details with the sanitized entity error message. */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", LogSanitizer.sanitizeException(ex));

        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.NOT_FOUND, LogSanitizer.sanitizeException(ex));
        problemDetail.setTitle("Resource Not Found");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/not-found"));
        problemDetail.setProperty("timestamp", Instant.now());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    /**
     * Returns HTTP 400 problem details with field violations sorted by field name. Non-field errors
     * are omitted.
     *
     * @throws NullPointerException if a field error has no default message or field names cannot be
     *     compared because one is null
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<@NonNull ProblemDetail> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", LogSanitizer.sanitizeException(ex));

        List<ApiValidationError> validationErrorsList =
                ex.getAllErrors().stream()
                        .filter(FieldError.class::isInstance)
                        .map(FieldError.class::cast)
                        .map(
                                fieldError ->
                                        new ApiValidationError(
                                                fieldError.getObjectName(),
                                                fieldError.getField(),
                                                fieldError.getRejectedValue(),
                                                Objects.requireNonNull(
                                                        fieldError.getDefaultMessage())))
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

    /** Returns HTTP 400 problem details with the sanitized constraint violation message. */
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

    /** Returns HTTP 400 problem details with the sanitized argument error message. */
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

    /** Returns HTTP 500 problem details with a generic message for an unexpected error. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<@NonNull ProblemDetail> handleGenericException(Exception ex) {
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
