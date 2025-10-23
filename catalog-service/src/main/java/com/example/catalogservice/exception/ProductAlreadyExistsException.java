/***
<p>
    Licensed under MIT License Copyright (c) 2022-2025 Raja Kolli.
</p>
***/

package com.example.catalogservice.exception;

import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ProductAlreadyExistsException extends ErrorResponseException {
    public ProductAlreadyExistsException(String productCode) {
        super(
                HttpStatus.CONFLICT,
                asProblemDetail("Product with code " + productCode + " already exists"),
                null);
    }

    private static ProblemDetail asProblemDetail(String message) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message);
        problemDetail.setTitle("Product Already Exists");
        problemDetail.setType(URI.create("https://api.microservices.com/errors/already-exists"));
        problemDetail.setProperty("errorCategory", "Generic");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
