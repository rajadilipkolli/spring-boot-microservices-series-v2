/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class CustomResponseStatusException extends ErrorResponseException {

    /**
     * Creates an HTTP error whose problem detail uses the supplied status and message.
     *
     * @param status the status for both the response and its problem detail
     * @param message the problem detail text
     */
    public CustomResponseStatusException(HttpStatusCode status, String message) {
        super(status, problemDetail(status, message), null);
    }

    private static ProblemDetail problemDetail(HttpStatusCode status, String message) {
        return ProblemDetail.forStatusAndDetail(status, message);
    }
}
