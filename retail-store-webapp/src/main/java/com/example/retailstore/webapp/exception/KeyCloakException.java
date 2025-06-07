package com.example.retailstore.webapp.exception;

import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class KeyCloakException extends ErrorResponseException {

    public KeyCloakException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, asProblemDetail("Failed to register user: " + message), null);
    }

    private static ProblemDetail asProblemDetail(String errorMessage) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, errorMessage);
        problemDetail.setTitle("Keycloak Registration Error");
        problemDetail.setType(URI.create("https://api.retailstore.com/errors/keycloak-registration"));
        problemDetail.setProperty("errorCategory", "Generic");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
