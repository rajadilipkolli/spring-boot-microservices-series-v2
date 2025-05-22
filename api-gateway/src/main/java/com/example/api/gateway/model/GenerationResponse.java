/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.api.gateway.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Response containing data generation results")
public record GenerationResponse(
        @Schema(description = "Status of the generation process") String status,
        @Schema(description = "Detailed message about the generation process") String message,
        @Schema(description = "Map of service responses") Map<String, String> serviceResponses) {}
