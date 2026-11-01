/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.services.search;

import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.repositories.OrderSearchRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class SimilarityOrderSearchStrategy implements OrderSearchStrategy {

    private final OrderSearchRepository orderSearchRepository;

    public SimilarityOrderSearchStrategy(OrderSearchRepository orderSearchRepository) {
        this.orderSearchRepository = orderSearchRepository;
    }

    @Override
    public boolean supports(String mode) {
        return "similarity".equalsIgnoreCase(mode);
    }

    /** Uses a threshold of 0.3 when none is provided; repository failures propagate. */
    @Override
    public Page<Long> search(
            String term, Long customerId, OrderStatus status, Double threshold, Pageable pageable) {
        double actualThreshold = threshold != null ? threshold : 0.3;
        return orderSearchRepository.searchOrdersBySimilarity(
                term, actualThreshold, customerId, status != null ? status.name() : null, pageable);
    }
}
