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
public class KeywordOrderSearchStrategy implements OrderSearchStrategy {

    private final OrderSearchRepository orderSearchRepository;

    public KeywordOrderSearchStrategy(OrderSearchRepository orderSearchRepository) {
        this.orderSearchRepository = orderSearchRepository;
    }

    @Override
    public boolean supports(String mode) {
        return !"similarity".equalsIgnoreCase(mode);
    }

    @Override
    public Page<Long> search(
            String term, Long customerId, OrderStatus status, Double threshold, Pageable pageable) {
        return orderSearchRepository.searchOrdersByKeyword(term, customerId, status, pageable);
    }
}
