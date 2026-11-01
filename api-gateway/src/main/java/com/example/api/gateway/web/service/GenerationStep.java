/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.web.service;

import com.example.api.gateway.model.ServiceResult;
import com.example.api.gateway.model.ServiceType;
import reactor.core.publisher.Mono;

public interface GenerationStep {

    ServiceType getServiceType();

    default String getStepName() {
        return getServiceType() != null ? getServiceType().getDisplayName() : "Unknown";
    }

    Mono<ServiceResult> execute(String batchId, Integer batchSize);
}
