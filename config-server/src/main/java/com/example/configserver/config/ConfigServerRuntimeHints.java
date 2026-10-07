/* Licensed under Apache-2.0 2026 */
package com.example.configserver.config;

import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import java.util.Collections;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

public class ConfigServerRuntimeHints implements RuntimeHintsRegistrar {
    /**
     * Registers reflection hints for the OpenTelemetry Logback appender and Protobuf registry in
     * native images.
     *
     * @param hints the runtime hints to update
     * @param classLoader the class loader supplied for hint registration
     */
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection()
                .registerType(
                        OpenTelemetryAppender.class,
                        hint ->
                                hint.withConstructor(Collections.emptyList(), ExecutableMode.INVOKE)
                                        .withMethod(
                                                "setCaptureMdcAttributes",
                                                Collections.singletonList(
                                                        TypeReference.of(boolean.class)),
                                                ExecutableMode.INVOKE));
        hints.reflection()
                .registerType(
                        TypeReference.of("com.google.protobuf.ExtensionRegistry"),
                        hint ->
                                hint.withMethod(
                                        "getEmptyRegistry",
                                        Collections.emptyList(),
                                        ExecutableMode.INVOKE));
    }
}
