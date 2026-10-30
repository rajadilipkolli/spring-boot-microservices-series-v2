/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.web.ReactiveProblemDetailTraceIdEnricher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@DisabledInNativeImage
class CommonReactiveWebAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner reactiveContextRunner =
            new ReactiveWebApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(CommonReactiveWebAutoConfiguration.class));

    private final ApplicationContextRunner nonWebContextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(CommonReactiveWebAutoConfiguration.class));

    @Test
    void registersReactiveProblemDetailTraceIdEnricherInReactiveWebApplication() {
        reactiveContextRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ReactiveProblemDetailTraceIdEnricher.class);
                    assertThat(context).hasBean("reactiveProblemDetailTraceIdEnricher");
                    assertThat(context).hasBean("reactiveProblemDetailWebFluxConfigurer");
                });
    }

    @Test
    void backsOffWhenUserDefinesCustomReactiveEnricher() {
        reactiveContextRunner
                .withUserConfiguration(CustomReactiveEnricherConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context)
                                    .hasSingleBean(ReactiveProblemDetailTraceIdEnricher.class);
                            assertThat(context).hasBean("customReactiveEnricher");
                            assertThat(context)
                                    .doesNotHaveBean("reactiveProblemDetailTraceIdEnricher");
                        });
    }

    @Test
    void backsOffInNonWebApplication() {
        nonWebContextRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ReactiveProblemDetailTraceIdEnricher.class);
                });
    }

    @Test
    void backsOffWhenWebFluxConfigurerIsAbsent() {
        reactiveContextRunner
                .withClassLoader(new FilteredClassLoader(WebFluxConfigurer.class))
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context)
                                    .doesNotHaveBean(ReactiveProblemDetailTraceIdEnricher.class);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomReactiveEnricherConfiguration {

        @Bean
        ReactiveProblemDetailTraceIdEnricher customReactiveEnricher() {
            return new ReactiveProblemDetailTraceIdEnricher();
        }
    }
}
