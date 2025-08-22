/***
<p>
    Licensed under MIT License Copyright (c) 2022-2025 Raja Kolli.
</p>
***/

package com.example.orderservice.exception;

import java.util.List;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(List<String> productIds) {
        super("One or More products Not found from " + productIds);
    }
}
