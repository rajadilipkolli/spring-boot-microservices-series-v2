/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import com.example.orderservice.model.Address;
import com.example.orderservice.model.dtos.OrderDto;
import com.example.orderservice.model.response.OrderItemResponse;
import com.example.orderservice.model.response.OrderResponse;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class OrderServiceRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Registers reflection access for order DTOs, responses, and addresses in native images.
     *
     * @param hints registry to update with constructor and method access, plus field access for
     *     DTOs, responses, and addresses
     * @param classLoader unused by this registrar
     */
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        hints.reflection()
                .registerType(
                        OrderDto.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        OrderDto.OrderItemDto.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        OrderResponse.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        Address.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS)
                .registerType(
                        OrderItemResponse.class,
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS);
    }
}
