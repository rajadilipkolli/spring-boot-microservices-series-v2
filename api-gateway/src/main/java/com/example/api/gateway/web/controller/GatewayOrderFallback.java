/* Licensed under Apache-2.0 2023 */
package com.example.api.gateway.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/fallback/api/order")
public class GatewayOrderFallback {

    @GetMapping("/{id}")
    public Mono<String> fallback(@PathVariable Long id) {
        return Mono.just("Hello %d".formatted(id));
    }
}
