/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services.provider;

import com.example.paymentservice.repositories.CustomerBalanceRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Handles RESERVE operations by transferring available balance to reserved funds. */
@Component
public class ReserveLedgerOperationStrategy implements LedgerOperationStrategy {

    @Override
    public String getOperationType() {
        return "RESERVE";
    }

    @Override
    public boolean execute(
            CustomerBalanceRepository customerRepository, Long customerId, BigDecimal amount) {
        return customerRepository.reserveFunds(customerId, amount);
    }
}
