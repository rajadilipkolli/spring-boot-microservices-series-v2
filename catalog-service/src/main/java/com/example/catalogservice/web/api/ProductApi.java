/***
<p>
    Licensed under MIT License Copyright (c) 2025 Raja Kolli.
</p>
***/

package com.example.catalogservice.web.api;

import com.example.catalogservice.model.response.PagedResult;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.catalogservice.utils.AppConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

@Tag(name = "product")
public interface ProductApi {

    @Operation(
            summary = "Search products by term, price range, or both",
            description = "Allows searching for products based on a text term and/or price range",
            tags = {"product-search"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful search operation",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = PagedResult.class))
                        }),
                @ApiResponse(
                        responseCode = "400",
                        description = "Bad Request",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    public Mono<PagedResult<ProductResponse>> searchProducts(
            @Parameter(
                            name = "term",
                            description = "Text to search for in product name or description")
                    @RequestParam(required = false)
                    String term,
            @Parameter(
                            name = "minPrice",
                            description = "Minimum price range for filtering products")
                    @RequestParam(required = false)
                    Double minPrice,
            @Parameter(
                            name = "maxPrice",
                            description = "Maximum price range for filtering products")
                    @RequestParam(required = false)
                    Double maxPrice,
            @Parameter(
                            name = "pageNo",
                            description = "Page number (1-based)",
                            example = AppConstants.DEFAULT_PAGE_NUMBER)
                    @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER, required = false)
                    int pageNo,
            @Parameter(
                            name = "pageSize",
                            description = "Number of items per page",
                            example = AppConstants.DEFAULT_PAGE_SIZE)
                    @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE, required = false)
                    int pageSize,
            @Parameter(
                            name = "sortBy",
                            description = "Field to sort by",
                            example = AppConstants.DEFAULT_SORT_BY)
                    @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY, required = false)
                    String sortBy,
            @Parameter(
                            name = "sortDir",
                            description = "Sort direction (asc or desc)",
                            example = AppConstants.DEFAULT_SORT_DIRECTION)
                    @RequestParam(
                            defaultValue = AppConstants.DEFAULT_SORT_DIRECTION,
                            required = false)
                    String sortDir);
}
