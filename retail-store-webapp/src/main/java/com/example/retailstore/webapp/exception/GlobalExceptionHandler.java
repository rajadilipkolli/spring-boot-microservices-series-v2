package com.example.retailstore.webapp.exception;

import com.example.retailstore.webapp.util.LogSanitizer;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidationExceptions(MethodArgumentNotValidException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Validation Error");
        problemDetail.setType(URI.create("https://api.retailstore.com/errors/validation"));

        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());

        problemDetail.setProperty("errors", errors);
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolationException(ConstraintViolationException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Constraint Violation");
        problemDetail.setType(URI.create("https://api.retailstore.com/errors/constraint-violation"));

        List<String> errors = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.toList());

        problemDetail.setProperty("errors", errors);
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler({HttpClientErrorException.class, HttpServerErrorException.class})
    ProblemDetail handleHttpException(Exception ex) {
        log.error("HTTP error occurred: {}", LogSanitizer.sanitizeException(ex));
        HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof HttpClientErrorException clientError) {
            status = clientError.getStatusCode();
        } else if (ex instanceof HttpServerErrorException serverError) {
            status = serverError.getStatusCode();
        }

        ProblemDetail problemDetail = ProblemDetail.forStatus(status);
        problemDetail.setTitle("Service Error");
        problemDetail.setType(URI.create("https://api.retailstore.com/errors/service-error"));
        problemDetail.setDetail(LogSanitizer.sanitizeException(ex));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail handleInvalidRequestException(InvalidRequestException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.retailstore.com/errors/bad-request"));
        problemDetail.setDetail(LogSanitizer.sanitizeException(ex));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleResourceNotFoundException(ResourceNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problemDetail.setTitle("Not Found");
        problemDetail.setType(URI.create("https://api.retailstore.com/errors/not-found"));
        problemDetail.setDetail(LogSanitizer.sanitizeException(ex));
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleSpringAccessDeniedException(AccessDeniedException ex) {
        log.warn(
                "Access Denied (e.g., by @PreAuthorize), handled in GlobalExceptionHandler: {}",
                LogSanitizer.sanitizeException(ex));
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problemDetail.setTitle("Forbidden");
        problemDetail.setType(URI.create("https://api.retailstore.com/errors/forbidden"));
        problemDetail.setDetail("You do not have the necessary permissions to access this resource.");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: {}", LogSanitizer.sanitizeException(ex));
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create("https://api.retailstore.com/errors/internal-error"));
        problemDetail.setDetail("An unexpected error occurred. Please try again later.");
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
