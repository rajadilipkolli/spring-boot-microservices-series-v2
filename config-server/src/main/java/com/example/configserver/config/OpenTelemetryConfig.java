/* Licensed under Apache-2.0 2026 */
package com.example.configserver.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenTelemetryConfig {
    /**
     * Installs the application's OpenTelemetry instance in the Logback appender.
     *
     * @param openTelemetry the OpenTelemetry instance managed by Spring
     */
    OpenTelemetryConfig(OpenTelemetry openTelemetry) {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
