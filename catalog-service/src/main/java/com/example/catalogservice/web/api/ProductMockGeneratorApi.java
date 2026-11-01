/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.web.api;

import com.example.catalogservice.model.response.GenerateProductsResponse;
import com.example.catalogservice.services.ProductGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

@Tag(name = "product-generator")
public interface ProductMockGeneratorApi {

    /**
     * Generates and saves sample products, reusing products with matching codes. New or restored
     * products produce creation outbox events, and product caches are invalidated. Save and
     * cache-generation errors propagate through the returned Mono; Redis deletion failures are
     * suppressed.
     *
     * @param idempotencyKey included in each product code with its zero-based batch index
     * @param batchSize number of products from 1 through 10,000, or null for 101
     * @return a successful generation result after all saves complete
     * @throws IllegalArgumentException if a supplied batch size is outside the allowed range
     */
    @Operation(
            summary = "Generate random products",
            description = "Generates and saves sample products in batches.",
            tags = {"product-generator"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Random products generated successfully",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GenerateProductsResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    Mono<GenerateProductsResponse> createRandomProducts(
            @Parameter(
                            name = "Idempotency-Key",
                            description = "Idempotency key for batch generation")
                    @RequestHeader(name = "Idempotency-Key")
                    String idempotencyKey,
            @Parameter(
                            name = "batchSize",
                            description = "Number of products to generate (between 1 and 10000)")
                    @RequestParam(required = false)
                    @Min(1)
                    @Max(ProductGeneratorService.MAX_GENERATION_BATCH_SIZE)
                    Integer batchSize);
}
