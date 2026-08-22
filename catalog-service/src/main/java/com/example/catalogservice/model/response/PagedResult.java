/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.data.domain.Page;

public record PagedResult<T>(
        List<T> data,
        Long totalElements,
        Integer pageNumber,
        Integer totalPages,
        @JsonProperty("isFirst") Boolean isFirst,
        @JsonProperty("isLast") Boolean isLast,
        @JsonProperty("hasNext") Boolean hasNext,
        @JsonProperty("hasPrevious") Boolean hasPrevious) {
    public PagedResult(Page<T> page) {
        this(
                page.getContent(),
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext(),
                page.hasPrevious());
    }
}
