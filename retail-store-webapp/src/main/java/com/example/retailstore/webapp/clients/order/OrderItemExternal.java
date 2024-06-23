package com.example.retailstore.webapp.clients.order;

import java.math.BigDecimal;

record OrderItemExternal(String productCode, int quantity, BigDecimal productPrice) {}
