/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services.provider;

import com.example.paymentservice.repositories.CustomerBalanceRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Handles CAPTURE operations by deducting reserved balance upon order confirmation. */
@Component
public class CaptureLedgerOperationStrategy implements LedgerOperationStrategy {

    @Override
    public String getOperationType() {
        return "CAPTURE";
    }

    @Override
    public boolean execute(
            CustomerBalanceRepository customerRepository, Long customerId, BigDecimal amount) {
        return customerRepository.captureFunds(customerId, amount);
    }
}
