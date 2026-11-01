/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.orderservice.model.response.ProductVerificationResult;
import java.util.List;

/** Domain port for verifying product existence across external catalogs. */
public interface ProductVerificationPort {

    /**
     * Checks if all provided product codes exist in the product catalog.
     *
     * @param productCodes list of product codes to verify
     * @return response indicating if products exist
     */
    ProductVerificationResult productsExistsByCodes(List<String> productCodes);
}
