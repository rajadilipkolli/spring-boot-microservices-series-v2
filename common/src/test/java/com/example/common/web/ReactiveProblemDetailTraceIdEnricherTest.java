/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;

class ReactiveProblemDetailTraceIdEnricherTest {

    private ReactiveProblemDetailTraceIdEnricher enricher;

    @BeforeEach
    void setUp() {
        enricher = new ReactiveProblemDetailTraceIdEnricher();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void handleErrorEnrichesProblemDetailFromErrorResponseHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Trace-Id", "header-trace-12345");
        ErrorResponse errorResponse =
                ErrorResponse.builder(new RuntimeException("boom"), HttpStatus.BAD_REQUEST, "boom")
                        .headers(h -> h.addAll(headers))
                        .build();

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        enricher.handleError(problemDetail, errorResponse);

        assertThat(problemDetail.getProperties()).isNotNull();
        assertThat(problemDetail.getProperties().get("traceId")).isEqualTo("header-trace-12345");
    }

    @Test
    void handleErrorEnrichesProblemDetailFromMdcWhenHeaderAbsent() {
        MDC.put("traceId", "error-trace-7777");

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        enricher.handleError(problemDetail, null);

        assertThat(problemDetail.getProperties()).isNotNull();
        assertThat(problemDetail.getProperties().get("traceId")).isEqualTo("error-trace-7777");
    }

    @Test
    void handleErrorEnrichesProblemDetailFromUnderscoreMdc() {
        MDC.put("trace_id", "underscore-error-6666");

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        enricher.handleError(problemDetail, null);

        assertThat(problemDetail.getProperties()).isNotNull();
        assertThat(problemDetail.getProperties().get("traceId")).isEqualTo("underscore-error-6666");
    }

    @Test
    void handleErrorPreservesExistingTraceId() {
        MDC.put("traceId", "new-trace-id");

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problemDetail.setProperty("traceId", "already-set-trace");

        enricher.handleError(problemDetail, null);

        assertThat(problemDetail.getProperties().get("traceId")).isEqualTo("already-set-trace");
    }

    @Test
    void handleErrorLeavesTraceIdAbsentWhenNoneAvailable() {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        enricher.handleError(problemDetail, null);

        assertThat(problemDetail.getProperties()).isNull();
    }
}
