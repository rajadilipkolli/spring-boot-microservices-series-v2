/***
<p>
    Licensed under MIT License Copyright (c) 2024-2025 Raja Kolli.
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
import reactor.core.scheduler.Schedulers;

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
        return Mono.just(productRequest)
                .map(productMapper::toProductDto)
                .flatMap(
                        productDto ->
                                Mono.fromCallable(
                                                () -> {
                                                    try {
                                                        return objectMapper.writeValueAsString(
                                                                productDto);
                                                    } catch (Exception e) {
                                                        throw new RuntimeException(
                                                                "Error serializing product", e);
                                                    }
                                                })
                                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(
                        productDtoAsString ->
                                Mono.just(
                                        streamBridge.send("inventory-out-0", productDtoAsString)));
    }
}
