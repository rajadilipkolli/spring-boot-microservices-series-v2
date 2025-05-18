/***
 <p>
 Licensed under MIT License Copyright (c) 2025 Raja Kolli.
 </p>
 ***/

package com.example.api.gateway.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

/** Controller that makes sequential service calls with a delay in between. */
@RestController
@RequestMapping("/api/generate")
public class GenerateController {

    private final WebClient webClient;
    private final Integer gatewayPort;

    public GenerateController(
            WebClient.Builder webClientBuilder,
            @Value("${gateway.port:null}") Integer gatewayPort) {
        this.webClient = webClientBuilder.build();
        this.gatewayPort = gatewayPort;
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
        URI uri = exchange.getRequest().getURI();
        return webClient
                .get()
                .uri(getURIFunction(uri, "/catalog-service/api/catalog/generate"))
                .retrieve()
                .bodyToMono(String.class)
                .onErrorResume(
                        e -> {
                            return Mono.just("Error calling catalog service: " + e.getMessage());
                        })
                .delayElement(Duration.ofSeconds(10))
                .flatMap(
                        catalogResponse ->
                                webClient
                                        .get()
                                        .uri(
                                                getURIFunction(
                                                        uri,
                                                        "/inventory-service/api/inventory/generate"))
                                        .retrieve()
                                        .bodyToMono(String.class)
                                        .onErrorResume(
                                                e -> {
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

    private Function<UriBuilder, URI> getURIFunction(URI uri, String path) {
        return uriBuilder -> {
            uriBuilder.port(Objects.requireNonNullElseGet(gatewayPort, uri::getPort));
            uriBuilder.scheme(uri.getScheme()).host(uri.getHost()).path(path);
            return uriBuilder.build();
        };
    }
}
