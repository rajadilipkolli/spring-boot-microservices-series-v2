/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.dto.OrderDto;
import com.example.common.dto.PagedResult;
import com.example.common.dto.ProductDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.data.domain.Page;

@DisabledInNativeImage
class CommonRuntimeHintsTest {

    private final CommonRuntimeHints registrar = new CommonRuntimeHints();

    @Test
    void registersPagedResultAndProductDtoWhenSpringDataPresent() {
        RuntimeHints hints = new RuntimeHints();
        registrar.registerHints(hints, getClass().getClassLoader());

        assertThat(RuntimeHintsPredicates.reflection().onType(PagedResult.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(ProductDto.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(OrderDto.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(OrderDto.OrderItemDto.class))
                .accepts(hints);
        assertThat(hints.reflection().typeHints())
                .map(TypeHint::getType)
                .containsExactlyInAnyOrder(
                        TypeReference.of(PagedResult.class),
                        TypeReference.of(ProductDto.class),
                        TypeReference.of(OrderDto.class),
                        TypeReference.of(OrderDto.OrderItemDto.class),
                        TypeReference.of("com.example.common.tsid.TsidFactorySupplier"));
        assertThat(hints.resources().resourcePatternHints()).isEmpty();
        assertThat(hints.proxies().jdkProxyHints()).isEmpty();
    }

    @Test
    void omitsPagedResultWhenSpringDataAbsent() {
        RuntimeHints hints = new RuntimeHints();
        ClassLoader filteredLoader = new FilteredClassLoader(Page.class);
        registrar.registerHints(hints, filteredLoader);

        assertThat(RuntimeHintsPredicates.reflection().onType(PagedResult.class)).rejects(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(ProductDto.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(OrderDto.class)).accepts(hints);
        assertThat(RuntimeHintsPredicates.reflection().onType(OrderDto.OrderItemDto.class))
                .accepts(hints);
        assertThat(hints.reflection().typeHints())
                .map(TypeHint::getType)
                .containsExactlyInAnyOrder(
                        TypeReference.of(ProductDto.class),
                        TypeReference.of(OrderDto.class),
                        TypeReference.of(OrderDto.OrderItemDto.class),
                        TypeReference.of("com.example.common.tsid.TsidFactorySupplier"));
        assertThat(hints.resources().resourcePatternHints()).isEmpty();
        assertThat(hints.proxies().jdkProxyHints()).isEmpty();
    }
}
