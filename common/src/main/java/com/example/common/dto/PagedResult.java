/***
 * <p>
 * Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Canonical pagination response wrapper used across all microservices.
 *
 * @param <T> element type of the contained page slice
 */
public record PagedResult<T>(
        List<T> data,
        long totalElements,
        int pageNumber,
        int totalPages,
        @JsonProperty("isFirst") boolean isFirst,
        @JsonProperty("isLast") boolean isLast,
        @JsonProperty("hasNext") boolean hasNext,
        @JsonProperty("hasPrevious") boolean hasPrevious)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a PagedResult from a Spring Data Page.
     *
     * @param page source Spring Data Page
     */
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

    /**
     * Constructs a PagedResult from a Spring Data Page and mapped content.
     *
     * @param <R> source element type
     * @param page source Spring Data Page
     * @param data mapped content list
     */
    public <R> PagedResult(Page<R> page, List<T> data) {
        this(
                data,
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext(),
                page.hasPrevious());
    }

    /**
     * Alternative constructor reconciling parameter ordering (data, page).
     *
     * @param <R> source element type
     * @param data mapped content list
     * @param page source Spring Data Page
     */
    public <R> PagedResult(List<T> data, Page<R> page) {
        this(page, data);
    }
}
