/* Licensed under Apache-2.0 2023 */
package com.example.orderservice.model.request;

import java.math.BigDecimal;

public record OrderItemRequest(String productId, int quantity, BigDecimal productPrice) {}
