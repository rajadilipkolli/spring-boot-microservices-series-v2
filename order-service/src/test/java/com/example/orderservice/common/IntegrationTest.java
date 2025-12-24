/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.common;

import com.example.orderservice.utils.AppConstants;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;

/**
 * Meta-annotation for integration tests. This combines all common test configuration annotations
 * into a single reusable annotation to avoid Spring Boot 4.0 annotation resolution issues with
 * abstract base classes.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ActiveProfiles({AppConstants.PROFILE_TEST})
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.cloud.config.enabled=false"},
        classes = {ContainersConfig.class, PostGreSQLContainer.class})
@AutoConfigureMockMvc
@AutoConfigureTracing
public @interface IntegrationTest {}
