/***
<p>
    Licensed under MIT License Copyright (c) 2025-2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response indicating success of product generation")
public record GenerateProductsResponse(
        @Schema(description = "True if products were generated successfully", example = "true")
                boolean success) {}
