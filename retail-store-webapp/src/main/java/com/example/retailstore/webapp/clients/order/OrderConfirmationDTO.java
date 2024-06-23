package com.example.retailstore.webapp.clients.order;

public record OrderConfirmationDTO(Long orderId, Long customerId, String status) {}
