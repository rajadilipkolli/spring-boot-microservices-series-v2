/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.api;

import com.example.api.gateway.model.GenerationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

@Tag(
        name = "Data Generation",
        description = "API for orchestrating data generation across services")
public interface GenerateAPI {

    int MAX_BATCH_SIZE = 10_000;

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Generate sample data across services",
            description =
                    "Orchestrates data generation calls to catalog and inventory services sequentially with smart error handling",
            tags = {"Data Generation"})
    @ApiResponses(
            value = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Data generation completed successfully"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Bad request - generation failed in one of the services"),
                @ApiResponse(
                        responseCode = "404",
                        description = "One of the required microservices was not found"),
                @ApiResponse(
                        responseCode = "408",
                        description = "Timeout occurred when calling one of the services"),
                @ApiResponse(
                        responseCode = "500",
                        description = "Internal server error during data generation"),
                @ApiResponse(
                        responseCode = "503",
                        description = "One of the required services is temporarily unavailable")
            })
    Mono<ResponseEntity<GenerationResponse>> generate(
            @RequestParam(required = false) @Min(1) @Max(MAX_BATCH_SIZE) Integer batchSize);
}
