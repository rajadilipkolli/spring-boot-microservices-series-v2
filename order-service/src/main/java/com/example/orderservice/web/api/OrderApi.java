/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.web.api;

import com.example.common.dto.PagedResult;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.model.response.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
@Tag(name = "order-controller", description = "the order-controller API")
public interface OrderApi {

    @Operation(
            summary = "fetches all orders with pagination",
            tags = {"order-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Success",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = PagedResult.class))
                        })
            })
    PagedResult<OrderResponse> getAllOrders(@Parameter(hidden = true) Pageable pageable);

    @Operation(
            summary = "fetches all orders for a customer",
            tags = {"order-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Success",
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
    ResponseEntity<PagedResult<OrderResponse>> ordersByCustomerId(
            @Parameter(name = "id", in = ParameterIn.PATH) Long id,
            @Parameter(hidden = true) Pageable pageable);

    /**
     * Searches orders by source, delivery address, or item product code with optional filters.
     *
     * @param term required search text
     * @param mode search mode; "similarity" selects trigram matching, ignoring case, and other
     *     values select keyword matching; defaults to "keyword" when omitted
     * @param customerId customer to filter by, or null for all customers
     * @param status status to filter by, or null for all statuses
     * @param threshold additional similarity cutoff from 0.0 to 1.0, defaulting to 0.3 when
     *     omitted; ignored in keyword mode
     * @param pageable pagination options
     * @return an HTTP 200 response containing matching orders and pagination metadata
     * @throws IllegalArgumentException if a supplied similarity threshold is outside 0.0 to 1.0
     */
    @Operation(
            summary = "searches orders based on a term and filters",
            tags = {"order-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Success",
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
    ResponseEntity<PagedResult<OrderResponse>> searchOrders(
            @Parameter(name = "term", in = ParameterIn.QUERY) @NotBlank String term,
            @Parameter(name = "mode", in = ParameterIn.QUERY) String mode,
            @Parameter(name = "customerId", in = ParameterIn.QUERY) Long customerId,
            @Parameter(name = "status", in = ParameterIn.QUERY) OrderStatus status,
            @Parameter(name = "threshold", in = ParameterIn.QUERY) Double threshold,
            @Parameter(hidden = true) Pageable pageable);
}
