package com.example.retailstore.webapp.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.HttpClientErrorException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    void handleValidationExceptions_shouldReturnProblemDetail() {
        // Arrange
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        FieldError fieldError = new FieldError("object", "field", "error message");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        // Act
        ProblemDetail result = exceptionHandler.handleValidationExceptions(ex);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo("Validation Error");
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) result.getProperties().get("errors");
        assertThat(errors).containsExactly("field: error message");
    }

    @Test
    void handleConstraintViolationException_shouldReturnProblemDetail() {
        // Arrange
        Set<ConstraintViolation<?>> violations = new HashSet<>();
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(mock(javax.validation.Path.class));
        when(violation.getPropertyPath().toString()).thenReturn("property");
        when(violation.getMessage()).thenReturn("constraint message");
        violations.add(violation);

        ConstraintViolationException ex = new ConstraintViolationException("message", violations);

        // Act
        ProblemDetail result = exceptionHandler.handleConstraintViolationException(ex);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo("Constraint Violation");
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) result.getProperties().get("errors");
        assertThat(errors).containsExactly("property: constraint message");
    }

    @Test
    void handleHttpClientErrorException_shouldReturnProblemDetail() {
        // Arrange
        HttpClientErrorException ex =
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null);

        // Act
        ProblemDetail result = exceptionHandler.handleHttpException(ex);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.getTitle()).isEqualTo("Service Error");
        assertThat(result.getDetail()).contains("404");
    }

    @Test
    void handleGenericException_shouldReturnProblemDetail() {
        // Arrange
        Exception ex = new RuntimeException("unexpected error");

        // Act
        ProblemDetail result = exceptionHandler.handleGenericException(ex);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(result.getTitle()).isEqualTo("Internal Server Error");
        assertThat(result.getDetail()).isEqualTo("An unexpected error occurred. Please try again later.");
    }
}
