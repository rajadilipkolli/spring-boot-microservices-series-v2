/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.orderservice.config.ApplicationProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogService {

    private final CatalogServiceProxy catalogServiceProxy;
    private final ApplicationProperties applicationProperties;

    @CircuitBreaker(name = "default", fallbackMethod = "productsExistsDefaultValue")
    public boolean productsExistsByCodes(List<String> productCodes) {
        return catalogServiceProxy.productsExistsByCodes(productCodes);
    }

    boolean productsExistsDefaultValue(List<String> productCodes, Exception e) {
        log.error(
                "While fetching status for productCodes :{}, Exception Occurred : {}",
                productCodes,
                e.getMessage());
        return applicationProperties.byPassCircuitBreaker();
    }
}
