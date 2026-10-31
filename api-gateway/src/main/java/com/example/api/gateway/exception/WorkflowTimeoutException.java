/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.api.gateway.exception;

public class WorkflowTimeoutException extends RuntimeException {

    public WorkflowTimeoutException(Throwable cause) {
        super("Generation workflow exceeded its deadline", cause);
    }
}
