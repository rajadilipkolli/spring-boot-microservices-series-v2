/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.config;

import com.example.common.dto.ProductDto;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.util.ClassUtils;

/**
 * Registers runtime reflection hints for common cross-cutting DTOs serialized/deserialized with
 * Jackson. Logging classes and aspects do not require reflection hints, and Java serialization
 * hints are not registered. Consuming services are responsible for registering reflection hints for
 * their specific {@code PagedResult<T>} element types.
 */
public class CommonRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Registers reflection access to declared constructors, methods, and fields of {@code
     * PagedResult} (if Spring Data's {@code Page} is on the classpath) and {@link ProductDto} for
     * native images.
     *
     * @param hints registry to update
     * @param classLoader classLoader to inspect for optional dependencies
     */
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        if (ClassUtils.isPresent("org.springframework.data.domain.Page", classLoader)) {
            hints.reflection()
                    .registerType(
                            TypeReference.of("com.example.common.dto.PagedResult"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                            MemberCategory.INVOKE_DECLARED_METHODS,
                            MemberCategory.ACCESS_DECLARED_FIELDS);
        }
        hints.reflection()
                .registerType(
                        ProductDto.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);
        if (ClassUtils.isPresent("io.hypersistence.tsid.TSID", classLoader)) {
            hints.reflection()
                    .registerType(
                            TypeReference.of("com.example.common.tsid.TsidFactorySupplier"),
                            MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
    }
}
