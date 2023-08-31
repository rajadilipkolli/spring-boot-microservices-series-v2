/*** Licensed under MIT License Copyright (c) 2023 Raja Kolli. ***/
package com.example.paymentservice.exception;

import java.net.URI;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

public class CustomerNotFoundException extends AbstractThrowableProblem {

    private static final URI TYPE = URI.create("https://api.customers.com/errors/not-found");

    public CustomerNotFoundException(Long customerId) {
        super(
                TYPE,
                "Customer Not Found",
                Status.NOT_FOUND,
                "Customer with Id '%d' not found".formatted(customerId));
    }
}
