package com.example.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;

@DisabledInNativeImage
class LogWriterTest {

    @Test
    void offProducesNoLogEvents() {
        assertLoggedLevel(LogLevel.OFF, null);
    }

    @ParameterizedTest
    @CsvSource({
        "TRACE, TRACE",
        "DEBUG, DEBUG",
        "INFO, INFO",
        "WARN, WARN",
        "ERROR, ERROR",
        "FATAL, ERROR"
    })
    void preservesExistingLevels(LogLevel logLevel, String expectedLevel) {
        assertLoggedLevel(logLevel, Level.valueOf(expectedLevel));
    }

    private void assertLoggedLevel(LogLevel logLevel, Level expectedLevel) {
        Logger logger = (Logger) LoggerFactory.getLogger(LogWriterTest.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.ALL);
        try {
            LogWriter.write(LogWriterTest.class, logLevel, "message");

            if (expectedLevel == null) {
                assertThat(appender.list).isEmpty();
            } else {
                assertThat(appender.list).hasSize(1);
                assertThat(appender.list.getFirst().getLevel()).isEqualTo(expectedLevel);
                assertThat(appender.list.getFirst().getFormattedMessage()).isEqualTo("message");
            }
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }
}
