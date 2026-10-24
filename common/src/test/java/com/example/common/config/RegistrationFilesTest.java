/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.core.io.ClassPathResource;

@DisabledInNativeImage
class RegistrationFilesTest {

    @Test
    void aotFactoriesListsCommonRuntimeHints() throws IOException {
        ClassPathResource resource = new ClassPathResource("META-INF/spring/aot.factories");
        assertThat(resource.exists()).isTrue();
        try (InputStream is = resource.getInputStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).contains("com.example.common.config.CommonRuntimeHints");
        }
    }

    @Test
    void autoConfigurationImportsListsCommonLoggingAutoConfiguration() throws IOException {
        ClassPathResource resource =
                new ClassPathResource(
                        "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        assertThat(resource.exists()).isTrue();
        try (InputStream is = resource.getInputStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content)
                    .contains("com.example.common.config.CommonLoggingAutoConfiguration");
        }
    }
}
