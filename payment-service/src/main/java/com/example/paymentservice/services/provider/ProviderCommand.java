/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services.provider;

import java.math.BigDecimal;

public record ProviderCommand(
        Long operationId, Long orderId, Long customerId, String operationType, BigDecimal amount) {}
