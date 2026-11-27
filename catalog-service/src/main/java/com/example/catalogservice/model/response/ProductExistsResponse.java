/***
<p>
    Licensed under MIT License Copyright (c) 2025-2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response indicating whether product exists")
public record ProductExistsResponse(
        @Schema(description = "True if product exists, false otherwise", example = "true")
                boolean exists) {}
