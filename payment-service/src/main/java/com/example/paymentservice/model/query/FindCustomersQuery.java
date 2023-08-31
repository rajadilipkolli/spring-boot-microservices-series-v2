/*** Licensed under MIT License Copyright (c) 2023 Raja Kolli. ***/
package com.example.paymentservice.model.query;

public record FindCustomersQuery(int pageNo, int pageSize, String sortBy, String sortDir) {}
