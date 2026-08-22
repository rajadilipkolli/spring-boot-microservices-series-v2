/* Licensed under Apache-2.0 2026 */
package com.example.configserver.config;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class ConfigServerRuntimeHints implements RuntimeHintsRegistrar {
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // Tracing agent captured hints for Spring Security proxy and methods.
        // Those are largely handled by AOT but keeping this registrar per instructions.
    }
}
