/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class CustomResponseStatusException extends ErrorResponseException {

    public CustomResponseStatusException(HttpStatusCode status, String message) {
        super(status, problemDetail(message), null);
    }

    private static ProblemDetail problemDetail(String message) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
