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
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
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
    public Mono<ResponseEntity<String>> generate(ServerWebExchange exchange) {
        // Log available services from Eureka
        List<String> services = discoveryClient.getServices();
        logger.info("Available services: {}", services);

        // Log instances for catalog-service
        List<ServiceInstance> catalogInstances = discoveryClient.getInstances("catalog-service");
        logger.info("catalog-service instances: {}", catalogInstances.size());
        catalogInstances.forEach(
                instance ->
                        logger.info(
                                "Instance - Host: {}, Port: {}, URI: {}",
                                instance.getHost(),
                                instance.getPort(),
                                instance.getUri()));

        // Log instances for inventory-service
        List<ServiceInstance> inventoryInstances =
                discoveryClient.getInstances("inventory-service");
        logger.info("inventory-service instances: {}", inventoryInstances.size());
        inventoryInstances.forEach(
                instance ->
                        logger.info(
                                "Instance - Host: {}, Port: {}, URI: {}",
                                instance.getHost(),
                                instance.getPort(),
                                instance.getUri()));

        return webClient
                .get()
                .uri("lb://catalog-service/api/catalog/generate")
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(
                        e -> {
                            logger.error("Error calling catalog service", e);
                            return Mono.just("Error calling catalog service: " + e.getMessage());
                        })
                .delayElement(Duration.ofSeconds(10))
                .flatMap(
                        catalogResponse ->
                                webClient
                                        .get()
                                        .uri("lb://inventory-service/api/inventory/generate")
                                        .retrieve()
                                        .bodyToMono(String.class)
                                        .onErrorResume(
                                                e -> {
                                                    logger.error(
                                                            "Error calling inventory service", e);
                                                    return Mono.just(
                                                            "Error calling inventory service: "
                                                                    + e.getMessage());
                                                })
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
}
