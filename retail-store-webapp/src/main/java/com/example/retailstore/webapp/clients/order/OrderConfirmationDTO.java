package com.example.retailstore.webapp.clients.order;

import com.fasterxml.jackson.annotation.JsonFormat;

public record OrderConfirmationDTO(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long orderId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long customerId,
        String status) {}
