/***
<p>
    Licensed under MIT License Copyright (c) 2025-2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.controller;

import com.example.api.gateway.model.GenerationResponse;
import com.example.api.gateway.web.api.GenerateAPI;
import com.example.api.gateway.web.service.GenerationOrchestrator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/generate")
public class GenerateController implements GenerateAPI {

    private final GenerationOrchestrator orchestrator;

    public GenerateController(GenerationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Override
    public Mono<ResponseEntity<GenerationResponse>> generate(
            @RequestParam(required = false) @Min(1) @Max(MAX_BATCH_SIZE) Integer batchSize) {

        return orchestrator
                .generate(batchSize)
                .map(
                        response -> {
                            if ("success".equalsIgnoreCase(response.status())) {
                                return ResponseEntity.ok(response);
                            }

                            return ResponseEntity.status(502).body(response);
                        });
    }
}
