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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class TraceIdSupportTest {

    @BeforeEach
    void setUp() {
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void resolveTraceIdPrefersHeaderSupplier() {
        MDC.put("traceId", "mdc-trace");
        String traceId = TraceIdSupport.resolveTraceId(() -> "header-trace");
        assertThat(traceId).isEqualTo("header-trace");
    }

    @Test
    void resolveTraceIdFallsBackToMdcTraceId() {
        MDC.put("traceId", "mdc-trace");
        String traceId = TraceIdSupport.resolveTraceId(() -> null);
        assertThat(traceId).isEqualTo("mdc-trace");
    }

    @Test
    void resolveTraceIdFallsBackToMdcTraceIdUnderscore() {
        MDC.put("trace_id", "underscore-trace");
        String traceId = TraceIdSupport.resolveTraceId(() -> "   ");
        assertThat(traceId).isEqualTo("underscore-trace");
    }

    @Test
    void resolveTraceIdReturnsNullWhenNoneAvailable() {
        String traceId = TraceIdSupport.resolveTraceId(null);
        assertThat(traceId).isNull();
    }

    @Test
    void enrichProblemDetailPreservesExistingTraceId() {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problemDetail.setProperty("traceId", "existing-id");

        String traceId = TraceIdSupport.enrichProblemDetail(problemDetail, () -> "header-id");

        assertThat(traceId).isEqualTo("existing-id");
        assertThat(problemDetail.getProperties().get("traceId")).isEqualTo("existing-id");
    }

    @Test
    void enrichProblemDetailAddsResolvedTraceId() {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        String traceId = TraceIdSupport.enrichProblemDetail(problemDetail, () -> "header-id");

        assertThat(traceId).isEqualTo("header-id");
        assertThat(problemDetail.getProperties()).isNotNull();
        assertThat(problemDetail.getProperties().get("traceId")).isEqualTo("header-id");
    }

    @Test
    void enrichProblemDetailReturnsNullWhenNoneAvailable() {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        String traceId = TraceIdSupport.enrichProblemDetail(problemDetail, null);

        assertThat(traceId).isNull();
        assertThat(problemDetail.getProperties()).isNull();
    }
}
