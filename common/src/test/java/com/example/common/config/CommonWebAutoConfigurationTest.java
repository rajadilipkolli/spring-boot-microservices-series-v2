/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.web.ProblemDetailTraceIdEnricher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@DisabledInNativeImage
class CommonWebAutoConfigurationTest {

    private final WebApplicationContextRunner webContextRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class));

    private final ApplicationContextRunner nonWebContextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class));

    @Test
    void registersProblemDetailTraceIdEnricherInServletWebApplication() {
        webContextRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProblemDetailTraceIdEnricher.class);
                    assertThat(context).hasBean("problemDetailTraceIdEnricher");
                });
    }

    @Test
    void backsOffWhenUserDefinesCustomProblemDetailTraceIdEnricher() {
        webContextRunner
                .withUserConfiguration(CustomEnricherConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(ProblemDetailTraceIdEnricher.class);
                            assertThat(context).hasBean("customEnricher");
                            assertThat(context).doesNotHaveBean("problemDetailTraceIdEnricher");
                        });
    }

    @Test
    void backsOffInNonWebApplication() {
        nonWebContextRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ProblemDetailTraceIdEnricher.class);
                });
    }

    @Test
    void backsOffWhenResponseBodyAdviceIsAbsent() {
        webContextRunner
                .withClassLoader(new FilteredClassLoader(ResponseBodyAdvice.class))
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean(ProblemDetailTraceIdEnricher.class);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomEnricherConfiguration {

        @Bean
        ProblemDetailTraceIdEnricher customEnricher() {
            return new ProblemDetailTraceIdEnricher();
        }
    }
}
