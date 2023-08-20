/*** Licensed under Apache-2.0 2023 ***/
package com.example.catalogservice.services;

import com.example.catalogservice.config.logging.Loggable;
import com.example.catalogservice.model.response.InventoryDto;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Loggable
public class InventoryServiceProxy {

    private final WebClient webClient;

    public InventoryServiceProxy(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<InventoryDto> getInventoryByProductCode(String productCode) {
        return webClient
                .get()
                .uri("/api/inventory/{productCode}", productCode)
                .retrieve()
                .bodyToMono(InventoryDto.class);
    }

    public Flux<InventoryDto> getInventoryByProductCodes(List<String> codes) {
        return webClient
                .get()
                .uri(
                        uriBuilder -> {
                            uriBuilder.path("/api/inventory/product");
                            uriBuilder.queryParam("codes", codes);
                            return uriBuilder.build();
                        })
                .retrieve()
                .bodyToFlux(InventoryDto.class);
    }
}
