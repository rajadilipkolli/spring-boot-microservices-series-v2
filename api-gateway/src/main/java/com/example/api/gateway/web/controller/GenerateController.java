/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Controller that makes sequential service calls with a delay in between. */
@RestController
@RequestMapping("/api/generate")
public class GenerateController {

    private static final Logger logger = LoggerFactory.getLogger(GenerateController.class);
    private final WebClient webClient;
    private final DiscoveryClient discoveryClient;

    public GenerateController(
            @LoadBalanced WebClient.Builder webClientBuilder, DiscoveryClient discoveryClient) {
        this.webClient = webClientBuilder.build();
        this.discoveryClient = discoveryClient;
    }

    /**
     * Endpoint that makes two sequential calls to services with a delay between them. 1. Call to
     * catalog-service 2. Wait for 10 seconds 3. Call to inventory-service
     *
     * @return Mono with response message
     */
    @GetMapping
    @Operation(summary = "Generate data by making sequential service calls with delay")
    public Mono<ResponseEntity<String>> generate() {
        // Log available services from Eureka
        List<String> services = discoveryClient.getServices();
        logger.info("Available services: {}", services);

        return callGetEndpoint(
                        "lb://CATALOG-SERVICE/catalog-service/api/catalog/generate",
                        "Error calling catalog service")
                .delayElement(Duration.ofSeconds(10))
                .flatMap(
                        catalogResponse ->
                                callGetEndpoint(
                                                "lb://inventory-service/api/inventory/generate",
                                                "Error calling inventory service")
                                        .map(
                                                inventoryResponse ->
                                                        ResponseEntity.ok(
                                                                "Generation process completed successfully. "
                                                                        + "Catalog response: "
                                                                        + catalogResponse
                                                                        + ", "
                                                                        + "Inventory response: "
                                                                        + inventoryResponse)));
    }

    private Mono<String> callGetEndpoint(String uri, String message) {
        return webClient
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(
                        e -> {
                            logger.error(message, e);
                            return Mono.just(message + e.getMessage());
                        });
    }
}
