/*** Licensed under MIT License Copyright (c) 2023 Raja Kolli. ***/
package com.example.retailstore.webapp.clients.customer;

public record CustomerResponse(
        Long customerId, String name, String email, String phone, String address, int amountAvailable) {}
