package com.example.retailstore.webapp.clients;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PagedResult<T>(
        List<T> data,
        Long totalElements,
        Integer pageNumber,
        Integer totalPages,
        @JsonProperty("isFirst") Boolean isFirst,
        @JsonProperty("isLast") Boolean isLast,
        @JsonProperty("hasNext") Boolean hasNext,
        @JsonProperty("hasPrevious") Boolean hasPrevious) {}
