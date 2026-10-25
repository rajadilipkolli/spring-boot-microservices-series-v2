/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Raw pagination values, validated before creating a pageable. */
@Schema(description = "Pagination and sorting request parameters")
public record OrderPaginationRequest(
        @Schema(description = "Zero-based page index", example = "0") @Min(0) Integer page,
        @Schema(description = "Page size (number of items)", example = "10") @Min(1) Integer size,
        @Schema(description = "Zero-based page index alias", example = "0") @Min(0) Integer pageNo,
        @Schema(description = "Page size alias", example = "10") @Min(1) Integer pageSize,
        @Schema(description = "Property name to sort by", example = "id") String sortBy,
        @Schema(description = "Sort direction (asc or desc)", example = "asc") String sortDir) {

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
     * Missing page and size values default to page 0 and 10 rows, and sizes are capped at 2000.
     * Legacy direction is ascending when absent, empty, or "asc" ignoring case; any other value
     * selects descending order.
     *
     * @param sort non-null resolved sorting; an unsorted value enables legacy sorting
     * @return pagination with a zero-based page number and the resolved sort orders
     * @throws IllegalArgumentException if a final sort property is unsupported, the resolved page
     *     number is negative, or the resolved size is less than one
     * @throws NullPointerException if sort is null
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
