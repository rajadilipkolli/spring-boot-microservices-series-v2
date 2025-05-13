/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.orderservice.exception;

import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class OrderNotFoundException extends ErrorResponseException {

    public OrderNotFoundException(Long orderId) {
        super(
                HttpStatus.NOT_FOUND,
                asProblemDetail("Order with Id " + orderId + " not found"),
                null);
    }

    private static ProblemDetail asProblemDetail(String errorMessage) {
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, errorMessage);
        problemDetail.setTitle("Order Not Found");
        problemDetail.setType(URI.create("http://api.orders.com/errors/not-found"));
        problemDetail.setProperty("errorCategory", "Generic");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
