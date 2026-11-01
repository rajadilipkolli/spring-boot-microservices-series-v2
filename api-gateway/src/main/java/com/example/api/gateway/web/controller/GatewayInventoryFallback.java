/***
<p>
    Licensed under MIT License Copyright (c) 2023-2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.controller;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/fallback/api/inventory")
public class GatewayInventoryFallback {

    @GetMapping({"", "/", "/{id}"})
    public Mono<ProblemDetail> fallback(@PathVariable(required = false) String id) {
        // IDs in this system are alphanumeric (e.g. P0001). Accept String to avoid
        // path variable conversion failures which can produce 404s when the
        // circuit-breaker fallback is invoked.
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        id != null && !id.isBlank()
                                ? "Inventory service is currently unavailable for product " + id
                                : "Inventory service is currently unavailable");
        problemDetail.setTitle("Inventory Service Unavailable");
        problemDetail.setType(URI.create("urn:problem-type:inventory-service-unavailable"));
        if (id != null && !id.isBlank()) {
            problemDetail.setProperty("productId", id);
        }
        return Mono.just(problemDetail);
    }
}
