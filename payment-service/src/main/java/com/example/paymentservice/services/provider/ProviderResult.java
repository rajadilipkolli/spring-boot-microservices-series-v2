/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services.provider;

public record ProviderResult(Status status, String outcome) {

    public enum Status {
        SUCCESS,
        REJECTED,
        NOT_FOUND
    }

    public static ProviderResult accept() {
        return new ProviderResult(Status.SUCCESS, "ACCEPT");
    }

    public static ProviderResult reject() {
        return new ProviderResult(Status.REJECTED, "REJECT");
    }

    public static ProviderResult notFound() {
        return new ProviderResult(Status.NOT_FOUND, null);
    }
}
