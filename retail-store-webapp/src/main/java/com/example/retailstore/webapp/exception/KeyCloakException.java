package com.example.retailstore.webapp.exception;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class KeyCloakException extends ErrorResponseException {

    // Cache the URI to avoid creating new objects on every exception
    private static final URI ERROR_TYPE_URI = URI.create("https://api.retailstore.com/errors/keycloak-registration");
    private static final String DEFAULT_TITLE = "Keycloak Registration Error";
    private static final String DEFAULT_CATEGORY = "Generic";

    // Pre-compiled pattern for efficient error message parsing
    private static final Pattern ERROR_PATTERN = Pattern.compile("^(\\d{3}):\\s*\\[(.+)]$");

    // Status code mapping for better performance than multiple string comparisons
    private static final Map<String, HttpStatus> STATUS_MAP = Map.of(
            "400", HttpStatus.BAD_REQUEST,
            "401", HttpStatus.UNAUTHORIZED,
            "403", HttpStatus.FORBIDDEN,
            "404", HttpStatus.NOT_FOUND,
            "409", HttpStatus.CONFLICT);

    public KeyCloakException(String message) {
        this(message, null);
    }

    public KeyCloakException(String message, Throwable e) {
        super(extractStatusFromMessage(message), createProblemDetailWithStatus(message), e);
    }

    /**
     * Extracts HTTP status from error message for ErrorResponseException constructor
     */
    private static HttpStatusCode extractStatusFromMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        Matcher matcher = ERROR_PATTERN.matcher(errorMessage.trim());
        if (matcher.matches()) {
            String statusCode = matcher.group(1);
            return STATUS_MAP.getOrDefault(statusCode, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * Creates ProblemDetail and extracts status in a single pass for better performance
     */
    private static ProblemDetail createProblemDetailWithStatus(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            return createDefaultProblemDetail("Unknown error occurred");
        }

        Matcher matcher = ERROR_PATTERN.matcher(errorMessage.trim());
        if (matcher.matches()) {
            String statusCode = matcher.group(1);
            String detail = matcher.group(2);
            HttpStatus status = STATUS_MAP.getOrDefault(statusCode, HttpStatus.INTERNAL_SERVER_ERROR);
            return createProblemDetail(status, detail);
        }

        // Fallback for non-standard error format
        return createDefaultProblemDetail(errorMessage);
    }

    private static ProblemDetail createProblemDetail(HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(DEFAULT_TITLE);
        problemDetail.setType(ERROR_TYPE_URI);
        problemDetail.setProperty("errorCategory", DEFAULT_CATEGORY);
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    private static ProblemDetail createDefaultProblemDetail(String detail) {
        return createProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, detail);
    }
}
