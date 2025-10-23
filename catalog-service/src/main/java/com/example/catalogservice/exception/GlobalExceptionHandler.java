/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.catalogservice.exception;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

@RestControllerAdvice
@Order(0)
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WebExchangeBindException.class)
    Mono<ProblemDetail> handleWebExchangeBindException(WebExchangeBindException ex) {
        log.warn("Validation error", ex);
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Validation Error");
        problemDetail.setDetail("Invalid request content.");
        problemDetail.setProperty("timestamp", Instant.now());
        // Build violations list
        var violations =
                ex.getFieldErrors().stream()
                        .map(
                                fieldError ->
                                        Map.of(
                                                "field", fieldError.getField(),
                                                "message", fieldError.getDefaultMessage()))
                        .toList();
        problemDetail.setProperty("violations", violations);
        return Mono.just(problemDetail);
    }

    @ExceptionHandler(DataBufferLimitException.class)
    Mono<ProblemDetail> handleDataBufferLimitException(DataBufferLimitException ex) {
        log.warn("Request body too large", ex);
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.PAYLOAD_TOO_LARGE);
        problemDetail.setTitle("Request Entity Too Large");
        problemDetail.setDetail("The request body exceeds the maximum allowed size");
        return Mono.just(problemDetail);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    Mono<ProblemDetail> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problemDetail.setTitle("Data Integrity Violation");
        problemDetail.setDetail("A conflict occurred while trying to save the data");
        problemDetail.setType(
                URI.create("https://api.microservices.com/errors/constraint-violation"));
        problemDetail.setProperty("errorCategory", "Database");
        problemDetail.setProperty("timestamp", Instant.now());
        return Mono.just(problemDetail);
    }
}
