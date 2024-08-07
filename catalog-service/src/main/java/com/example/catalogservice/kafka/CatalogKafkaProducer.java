/***
<p>
    Licensed under MIT License Copyright (c) 2024 Raja Kolli.
</p>
***/

package com.example.catalogservice.kafka;

import com.example.catalogservice.config.logging.Loggable;
import com.example.catalogservice.mapper.ProductMapper;
import com.example.catalogservice.model.request.ProductRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Loggable
public class CatalogKafkaProducer {

    private final StreamBridge streamBridge;
    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;

    public CatalogKafkaProducer(
            StreamBridge streamBridge, ProductMapper productMapper, ObjectMapper objectMapper) {
        this.streamBridge = streamBridge;
        this.productMapper = productMapper;
        this.objectMapper = objectMapper;
    }

    public Mono<Boolean> send(ProductRequest productRequest) {
        return Mono.fromCallable(
                        () -> {
                            // Convert ProductRequest to JSON
                            return this.objectMapper.writeValueAsString(
                                    this.productMapper.toProductDto(productRequest));
                        })
                .flatMap(
                        productDtoAsString ->
                                Mono.fromCallable(
                                        () -> {
                                            // Send the message via StreamBridge
                                            return streamBridge.send(
                                                    "inventory-out-0", productDtoAsString);
                                        }));
    }
}
