/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class CustomResponseStatusExceptionTest {

    @Test
    void problemDetailStatusShouldMatchConstructorStatus() {
        HttpStatus status = HttpStatus.NOT_FOUND;
        String message = "Resource was not found";

        CustomResponseStatusException exception =
                new CustomResponseStatusException(status, message);

        ProblemDetail problemDetail = exception.getBody();
        assertThat(problemDetail).isNotNull();
        assertThat(problemDetail.getStatus()).isEqualTo(status.value());
        assertThat(problemDetail.getDetail()).isEqualTo(message);
        assertThat(exception.getStatusCode()).isEqualTo(status);
    }

    @Test
    void problemDetailShouldReflectBadRequest() {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message = "Invalid input";

        CustomResponseStatusException exception =
                new CustomResponseStatusException(status, message);

        assertThat(exception.getBody().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(exception.getBody().getDetail()).isEqualTo(message);
    }
}
