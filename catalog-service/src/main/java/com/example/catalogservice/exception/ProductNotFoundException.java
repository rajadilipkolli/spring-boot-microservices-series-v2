package com.example.catalogservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

import java.net.URI;
import java.time.Instant;

public class ProductNotFoundException extends ErrorResponseException {

    public ProductNotFoundException(Long productId) {
        super(
                HttpStatus.NOT_FOUND,
                asProblemDetail("Product with id " + productId + " not found"),
                null);
    }

    private static ProblemDetail asProblemDetail(String message) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, message);
        problemDetail.setTitle("Product Not Found");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/not-found"));
        problemDetail.setProperty("errorCategory", "Generic");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
