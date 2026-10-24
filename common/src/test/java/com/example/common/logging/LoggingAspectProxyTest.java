/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.config.CommonLoggingAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@DisabledInNativeImage
@ExtendWith(OutputCaptureExtension.class)
class LoggingAspectProxyTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    AopAutoConfiguration.class,
                                    CommonLoggingAutoConfiguration.class))
                    .withUserConfiguration(TestServiceConfiguration.class);

    @Test
    void verifiesAopProxyAndInterception(CapturedOutput output) {
        contextRunner.run(
                context -> {
                    assertThat(context).hasNotFailed();

                    MethodAnnotatedService methodService =
                            context.getBean(MethodAnnotatedService.class);
                    ClassAnnotatedService classService =
                            context.getBean(ClassAnnotatedService.class);

                    assertThat(AopUtils.isAopProxy(methodService)).isTrue();
                    assertThat(AopUtils.isAopProxy(classService)).isTrue();

                    methodService.serve("hello");
                    assertThat(output.getOut())
                            .contains("serve() start execution")
                            .contains("serve() finished execution");

                    classService.performAction("world");
                    assertThat(output.getOut())
                            .contains("performAction() start execution")
                            .contains("performAction() finished execution");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestServiceConfiguration {
        @Bean
        MethodAnnotatedService methodAnnotatedService() {
            return new MethodAnnotatedService();
        }

        @Bean
        ClassAnnotatedService classAnnotatedService() {
            return new ClassAnnotatedService();
        }
    }

    static class MethodAnnotatedService {
        @Loggable
        public String serve(String message) {
            return "served: " + message;
        }
    }

    @Loggable
    static class ClassAnnotatedService {
        public String performAction(String action) {
            return "acted: " + action;
        }
    }
}
