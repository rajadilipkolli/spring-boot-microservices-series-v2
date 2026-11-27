/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.services.search;

import com.example.orderservice.entities.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderSearchStrategy {

    boolean supports(String mode);

    Page<Long> search(
            String term, Long customerId, OrderStatus status, Double threshold, Pageable pageable);
}
