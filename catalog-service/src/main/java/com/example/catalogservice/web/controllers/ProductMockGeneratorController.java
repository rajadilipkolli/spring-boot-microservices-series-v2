/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.web.controllers;

import com.example.catalogservice.model.response.GenerateProductsResponse;
import com.example.catalogservice.services.ProductGeneratorService;
import com.example.catalogservice.web.api.ProductMockGeneratorApi;
import com.example.common.logging.Loggable;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/catalog")
@Loggable
@Validated
public class ProductMockGeneratorController implements ProductMockGeneratorApi {

    private final ProductGeneratorService productGeneratorService;

    public ProductMockGeneratorController(ProductGeneratorService productGeneratorService) {
        this.productGeneratorService = productGeneratorService;
    }

    /**
     * Generates and saves sample products, reusing products with matching codes. Service errors
     * propagate.
     *
     * @param idempotencyKey included in each product code with its zero-based batch index
     * @param batchSize number of products from 1 through 10,000, or null for 101
     * @return the generation result after saves complete
     */
    @PostMapping(value = "/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    @Override
    public Mono<GenerateProductsResponse> createRandomProducts(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestParam(required = false)
                    @Min(1) @Max(ProductGeneratorService.MAX_GENERATION_BATCH_SIZE) Integer batchSize) {
        return productGeneratorService
                .generateAndSaveProducts(idempotencyKey, batchSize)
                .map(GenerateProductsResponse::new);
    }
}
