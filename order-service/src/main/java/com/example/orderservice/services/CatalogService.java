/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.orderservice.config.ApplicationProperties;
import com.example.orderservice.config.logging.Loggable;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@Loggable
public class CatalogService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final CatalogServiceProxy catalogServiceProxy;
    private final ApplicationProperties applicationProperties;

    public CatalogService(
            CatalogServiceProxy catalogServiceProxy, ApplicationProperties applicationProperties) {
        this.catalogServiceProxy = catalogServiceProxy;
        this.applicationProperties = applicationProperties;
    }

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
