package com.example.e2e.models;

import java.util.List;

public record OrderRequest(
        Long customerId, List<OrderItemRequest> items, Address deliveryAddress) {}
