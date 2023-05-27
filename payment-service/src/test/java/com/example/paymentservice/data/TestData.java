/* Licensed under Apache-2.0 2023 */
package com.example.paymentservice.data;

import com.example.paymentservice.entities.Customer;

public class TestData {
    public static Customer getCustomer() {
        return Customer.builder().id(1L).amountAvailable(1000).amountReserved(100).build();
    }
}
