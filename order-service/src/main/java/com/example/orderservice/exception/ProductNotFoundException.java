/***
<p>
    Licensed under MIT License Copyright (c) 2022-2023 Raja Kolli.
</p>
***/

package com.example.orderservice.exception;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ProductNotFoundException extends ErrorResponseException {

    public ProductNotFoundException(List<String> productIds) {
        super(
                HttpStatus.NOT_FOUND,
                asProblemDetail("One or More products Not found from " + productIds),
                null);
    }

    public ProductNotFoundException(Long id) {
        super(
                HttpStatus.NOT_FOUND,
                asProblemDetail("Product with Id - " + id + " Not found"),
                null);
    }

    private static ProblemDetail asProblemDetail(String errorMessage) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, errorMessage);
        problemDetail.setTitle("Product Not Found");
        problemDetail.setType(URI.create("http://api.products.com/errors/not-found"));
        problemDetail.setProperty("errorCategory", "Generic");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
