/***
 * <p>
 * Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.config;

import com.example.common.logging.LoggingAspect;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
@ConditionalOnClass(Aspect.class)
public class CommonLoggingAutoConfiguration {

    /**
     * Supplies the default logging aspect when AspectJ is available and no logging aspect bean
     * exists.
     *
     * @param env application environment used to determine production logging behavior
     * @return a logging aspect using the supplied environment
     */
    @Bean
    @ConditionalOnMissingBean
    LoggingAspect commonLoggingAspect(Environment env) {
        return new LoggingAspect(env);
    }
}
