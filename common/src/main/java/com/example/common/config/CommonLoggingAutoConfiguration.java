/***
 * <p>
 * Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.config;

import com.example.common.logging.LoggingAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
public class CommonLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LoggingAspect loggingAspect(Environment env) {
        return new LoggingAspect(env);
    }
}
