/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.logging.LoggingAspect;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;

@DisabledInNativeImage
class CommonLoggingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(CommonLoggingAutoConfiguration.class));

    @Test
    void autoConfigurationRegistersOneLoggingAspectWhenAspectJIsPresent() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LoggingAspect.class);
                    assertThat(context).hasBean("commonLoggingAspect");
                });
    }

    @Test
    void autoConfigurationBacksOffWhenUserDefinesLoggingAspect() {
        contextRunner
                .withUserConfiguration(CustomLoggingAspectConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(LoggingAspect.class);
                            assertThat(context).hasBean("customLoggingAspect");
                            assertThat(context).doesNotHaveBean("commonLoggingAspect");
                        });
    }

    @Test
    void autoConfigurationBacksOffWhenAspectJIsAbsent() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(Aspect.class))
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).doesNotHaveBean(LoggingAspect.class);
                        });
    }

    @Test
    void contextStartupSafeWhenSpringDataIsAbsent() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(Page.class))
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(LoggingAspect.class);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomLoggingAspectConfiguration {
        @Bean
        LoggingAspect customLoggingAspect(Environment env) {
            return new LoggingAspect(env);
        }
    }
}
