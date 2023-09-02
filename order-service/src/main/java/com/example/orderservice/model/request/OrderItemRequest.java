/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.model.request;

import java.math.BigDecimal;

public record OrderItemRequest(String productCode, int quantity, BigDecimal productPrice) {}
