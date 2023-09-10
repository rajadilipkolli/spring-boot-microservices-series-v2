/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.config;

import lombok.Data;

@Data
class Cors {
    private String pathPattern = "/api/**";
    private String allowedMethods = "*";
    private String allowedHeaders = "*";
    private String allowedOriginPatterns = "*";
    private boolean allowCredentials = true;
}
