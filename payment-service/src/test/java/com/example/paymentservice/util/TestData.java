/*** Licensed under MIT License Copyright (c) 2023-2024 Raja Kolli. ***/
package com.example.paymentservice.util;

import com.example.paymentservice.entities.Customer;

public class TestData {
    public static Customer getCustomer() {
        return new Customer().setId(1L).setAmountAvailable(1000).setAmountReserved(100);
    }
}
