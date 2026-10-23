/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.model.request;

import jakarta.validation.constraints.Min;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Raw pagination values, validated before creating a pageable. */
public record OrderPaginationRequest(
        @Min(0) Integer page,
        @Min(1) Integer size,
        @Min(0) Integer pageNo,
        @Min(1) Integer pageSize,
        String sortBy,
        String sortDir) {

    // Preserve Spring Data's default limit on the number of rows requested per page.
    private static final int MAX_PAGE_SIZE = 2000;
    private static final Set<String> ALLOWED_SORT_PROPERTIES =
            Set.of(
                    "id",
                    "customerId",
                    "status",
                    "source",
                    "version",
                    "createdBy",
                    "createdDate",
                    "lastModifiedBy",
                    "lastModifiedDate");

    /**
     * Creates pagination with modern parameters taking precedence over their legacy aliases. A
     * supplied Spring Data sort retains all orders; otherwise legacy sorting defaults to id ASC.
     *
     * @throws IllegalArgumentException if a final sort property is unsupported
     */
    public Pageable toPageable(Sort sort) {
        int pageNumber = page != null ? page : pageNo != null ? pageNo : 0;
        int requestedSize = size != null ? size : pageSize != null ? pageSize : 10;
        if (sort.isUnsorted()) {
            String property = sortBy == null || sortBy.isEmpty() ? "id" : sortBy;
            Sort.Direction direction =
                    sortDir == null || sortDir.isEmpty() || "asc".equalsIgnoreCase(sortDir)
                            ? Sort.Direction.ASC
                            : Sort.Direction.DESC;
            sort = Sort.by(direction, property);
        }
        for (Sort.Order order : sort) {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new IllegalArgumentException(
                        "Unsupported sort property: " + order.getProperty());
            }
        }
        return PageRequest.of(pageNumber, Math.min(requestedSize, MAX_PAGE_SIZE), sort);
    }
}
