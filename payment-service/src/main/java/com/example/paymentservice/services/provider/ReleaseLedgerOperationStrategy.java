/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services.provider;

import com.example.paymentservice.repositories.CustomerBalanceRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Handles RELEASE operations by restoring reserved funds back to available balance. */
@Component
public class ReleaseLedgerOperationStrategy implements LedgerOperationStrategy {

    @Override
    public String getOperationType() {
        return "RELEASE";
    }

    @Override
    public boolean execute(
            CustomerBalanceRepository customerRepository, Long customerId, BigDecimal amount) {
        return customerRepository.releaseFunds(customerId, amount);
    }
}
