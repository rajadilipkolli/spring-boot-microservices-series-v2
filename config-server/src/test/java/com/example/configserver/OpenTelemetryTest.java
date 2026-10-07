/* Licensed under Apache-2.0 2026 */
package com.example.configserver;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenTelemetryTest {

    @Autowired private OpenTelemetry openTelemetry;

    /** Verifies that OpenTelemetry is injected and its appender is attached to the root logger. */
    @Test
    void testOpenTelemetryAndAppender() {
        assertThat(openTelemetry).isNotNull();

        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Appender<ILoggingEvent> appender = rootLogger.getAppender("OpenTelemetry");

        assertThat(appender).isNotNull().isInstanceOf(OpenTelemetryAppender.class);
    }
}
