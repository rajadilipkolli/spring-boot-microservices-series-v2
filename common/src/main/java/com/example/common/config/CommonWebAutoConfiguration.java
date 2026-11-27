/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.config;

import com.example.common.web.ProblemDetailTraceIdEnricher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Auto-configuration for common web MVC components, including {@link ProblemDetailTraceIdEnricher}.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({ResponseBodyAdvice.class, ProblemDetail.class})
public class CommonWebAutoConfiguration {

    /**
     * Provides a default {@link ProblemDetailTraceIdEnricher} bean if one is not already defined.
     *
     * @return the trace ID enricher for {@link ProblemDetail} responses
     */
    @Bean
    @ConditionalOnMissingBean
    public ProblemDetailTraceIdEnricher problemDetailTraceIdEnricher() {
        return new ProblemDetailTraceIdEnricher();
    }
}
