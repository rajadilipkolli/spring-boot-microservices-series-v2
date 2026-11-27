/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.config;

import com.example.common.web.ReactiveProblemDetailTraceIdEnricher;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Auto-configuration for reactive WebFlux components, including {@link
 * ReactiveProblemDetailTraceIdEnricher}.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass({WebFluxConfigurer.class, ProblemDetail.class})
public class CommonReactiveWebAutoConfiguration {

    /**
     * Provides a default {@link ReactiveProblemDetailTraceIdEnricher} bean if one is not already
     * defined.
     *
     * @return the reactive trace ID enricher (functioning as both WebFilter and
     *     ErrorResponse.Interceptor)
     */
    @Bean
    @ConditionalOnMissingBean
    public ReactiveProblemDetailTraceIdEnricher reactiveProblemDetailTraceIdEnricher() {
        return new ReactiveProblemDetailTraceIdEnricher();
    }

    /**
     * Registers the enricher as an {@link ErrorResponse.Interceptor} with Spring WebFlux.
     *
     * @param enricher the trace ID enricher
     * @return a {@link WebFluxConfigurer} that registers the interceptor
     */
    @Bean
    public WebFluxConfigurer reactiveProblemDetailWebFluxConfigurer(
            ReactiveProblemDetailTraceIdEnricher enricher) {
        return new WebFluxConfigurer() {
            @Override
            public void addErrorResponseInterceptors(List<ErrorResponse.Interceptor> interceptors) {
                interceptors.add(enricher);
            }
        };
    }
}
