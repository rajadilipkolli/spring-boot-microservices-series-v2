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
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ProblemDetailTraceIdEnricherTest {

    private ProblemDetailTraceIdEnricher enricher;
    private MethodParameter dummyMethodParameter;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        enricher = new ProblemDetailTraceIdEnricher();
        dummyMethodParameter =
                new MethodParameter(
                        ProblemDetailTraceIdEnricherTest.class.getDeclaredMethod("dummyMethod"),
                        -1);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    ProblemDetail dummyMethod() {
        return null;
    }

    @Test
    void supportsReturnsTrue() {
        assertThat(enricher.supports(dummyMethodParameter, JacksonJsonHttpMessageConverter.class))
                .isTrue();
    }

    @Test
    void returnsNullWhenBodyIsNull() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);

        Object result =
                enricher.beforeBodyWrite(
                        null,
                        dummyMethodParameter,
                        MediaType.APPLICATION_JSON,
                        JacksonJsonHttpMessageConverter.class,
                        request,
                        response);

        assertThat(result).isNull();
    }

    @Test
    void returnsNonProblemDetailBodyUntouched() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);

        String body = "Hello World";
        Object result =
                enricher.beforeBodyWrite(
                        body,
                        dummyMethodParameter,
                        MediaType.TEXT_PLAIN,
                        JacksonJsonHttpMessageConverter.class,
                        request,
                        response);

        assertThat(result).isSameAs(body);
        assertThat(response.getHeaders().getFirst("X-Trace-Id")).isNull();
    }

    @Test
    void enrichesProblemDetailFromRequestHeader() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("X-Trace-Id", "trace-header-12345");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);

        Object result =
                enricher.beforeBodyWrite(
                        problemDetail,
                        dummyMethodParameter,
                        MediaType.APPLICATION_PROBLEM_JSON,
                        JacksonJsonHttpMessageConverter.class,
                        request,
                        response);

        assertThat(result).isSameAs(problemDetail);
        assertThat(problemDetail.getProperties()).isNotNull();
        assertThat(problemDetail.getProperties().get("traceId")).isEqualTo("trace-header-12345");
        assertThat(response.getHeaders().getFirst("X-Trace-Id")).isEqualTo("trace-header-12345");
    }

    @Test
    void enrichesProblemDetailFromMdcWhenRequestHeaderAbsent() {
        MDC.put("traceId", "mdc-trace-9999");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        Object result =
                enricher.beforeBodyWrite(
                        problemDetail,
                        dummyMethodParameter,
                        MediaType.APPLICATION_PROBLEM_JSON,
                        JacksonJsonHttpMessageConverter.class,
                        request,
                        response);

        assertThat(result).isSameAs(problemDetail);
        assertThat(problemDetail.getProperties()).isNotNull();
        assertThat(problemDetail.getProperties().get("traceId")).isEqualTo("mdc-trace-9999");
        assertThat(response.getHeaders().getFirst("X-Trace-Id")).isEqualTo("mdc-trace-9999");
    }

    @Test
    void enrichesProblemDetailFromUnderscoreMdcWhenTraceIdAbsent() {
        MDC.put("trace_id", "underscore-trace-8888");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        Object result =
                enricher.beforeBodyWrite(
                        problemDetail,
                        dummyMethodParameter,
                        MediaType.APPLICATION_PROBLEM_JSON,
                        JacksonJsonHttpMessageConverter.class,
                        request,
                        response);

        assertThat(result).isSameAs(problemDetail);
        assertThat(problemDetail.getProperties()).isNotNull();
        assertThat(problemDetail.getProperties().get("traceId")).isEqualTo("underscore-trace-8888");
        assertThat(response.getHeaders().getFirst("X-Trace-Id")).isEqualTo("underscore-trace-8888");
    }

    @Test
    void preservesExistingTraceIdInProblemDetail() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("X-Trace-Id", "new-trace-id");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);

        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problemDetail.setProperty("traceId", "explicitly-set-trace-id");

        Object result =
                enricher.beforeBodyWrite(
                        problemDetail,
                        dummyMethodParameter,
                        MediaType.APPLICATION_PROBLEM_JSON,
                        JacksonJsonHttpMessageConverter.class,
                        request,
                        response);

        assertThat(result).isSameAs(problemDetail);
        assertThat(problemDetail.getProperties().get("traceId"))
                .isEqualTo("explicitly-set-trace-id");
        assertThat(response.getHeaders().getFirst("X-Trace-Id"))
                .isEqualTo("explicitly-set-trace-id");
    }
}
