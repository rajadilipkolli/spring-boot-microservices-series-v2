/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.nativefixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.logging.LogWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class LogWriterNativeTest {

    @Test
    void verifiesLogWriterWritesAtVariousLevels(CapturedOutput output) {
        LogWriter.write(LogWriterNativeTest.class, LogLevel.WARN, "native-warn-message");
        LogWriter.write(LogWriterNativeTest.class, LogLevel.ERROR, "native-error-message");
        LogWriter.write(LogWriterNativeTest.class, LogLevel.FATAL, "native-fatal-message");
        LogWriter.write(LogWriterNativeTest.class, LogLevel.OFF, "native-off-message");

        assertThat(output.getOut())
                .contains("WARN")
                .contains("native-warn-message")
                .contains("ERROR")
                .contains("native-error-message")
                .contains("native-fatal-message")
                .doesNotContain("native-off-message");
    }
}
