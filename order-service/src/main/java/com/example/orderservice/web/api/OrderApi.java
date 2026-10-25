/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.web.api;

import com.example.common.dto.PagedResult;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.model.request.OrderPaginationRequest;
import com.example.orderservice.model.request.OrderRequest;
import com.example.orderservice.model.response.OrderResponse;
import com.example.orderservice.services.OrderGeneratorService;
import com.example.orderservice.web.controllers.OrderController.GenericResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
@Tag(name = "order-controller", description = "the order-controller API")
public interface OrderApi {

    /**
     * Returns orders using modern pagination parameters in preference to legacy aliases.
     *
     * @param pagination page and size values and legacy sorting options; omitted values default to
     *     page 0, size 10, and id ASC, with sizes capped at 2000
     * @param sort resolved sort orders, or an unsorted value to use legacy sorting
     * @return order responses and pagination metadata with a one-based response page number
     * @throws IllegalArgumentException if the resolved page is negative, the resolved size is less
     *     than one, or a resolved sort property is unsupported
     * @see OrderPaginationRequest#toPageable(Sort)
     */
    @Operation(
            summary = "fetches all orders with pagination",
            description =
                    "Accepts page/size and repeated sort=property,direction parameters, or legacy "
                            + "pageNo/pageSize/sortBy/sortDir parameters. Modern parameters take "
                            + "precedence when both forms are supplied. Pages start at zero; "
                            + "page sizes must be positive and are capped at 2000.",
            tags = {"order-controller"},
            responses = {@ApiResponse(responseCode = "200", description = "Success")})
    PagedResult<OrderResponse> getAllOrders(
            @Valid OrderPaginationRequest pagination, @Parameter(hidden = true) Sort sort);

    @Operation(
            summary = "fetches order by id",
            description = "Retrieves an order by its unique ID.",
            tags = {"order-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Order found",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = OrderResponse.class))
                        }),
                @ApiResponse(
                        responseCode = "404",
                        description = "Order not found",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "503",
                        description = "Service unavailable fallback",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = OrderResponse.class))
                        })
            })
    ResponseEntity<OrderResponse> getOrderById(
            @Parameter(name = "id", description = "Order ID", example = "1") @PathVariable Long id,
            @Parameter(name = "delay", description = "Simulated delay in seconds")
                    @RequestParam(required = false)
                    Integer delay);

    @Operation(
            summary = "creates a new order",
            description = "Creates a new order from the supplied request.",
            tags = {"order-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Order created",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = OrderResponse.class))
                        }),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid order request",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    ResponseEntity<OrderResponse> createOrder(@RequestBody @Valid OrderRequest orderRequest);

    @Operation(
            summary = "updates an existing order",
            description = "Updates an order by its ID.",
            tags = {"order-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Order updated",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = OrderResponse.class))
                        }),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid order request",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = ProblemDetail.class))
                        }),
                @ApiResponse(
                        responseCode = "404",
                        description = "Order not found",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    ResponseEntity<OrderResponse> updateOrder(
            @Parameter(name = "id", description = "Order ID", example = "1") @PathVariable Long id,
            @RequestBody @Valid OrderRequest orderRequest);

    @Operation(
            summary = "deletes an order",
            description = "Soft-deletes an active order.",
            tags = {"order-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "202",
                        description = "Order deletion accepted",
                        content = @Content),
                @ApiResponse(
                        responseCode = "404",
                        description = "Order not found",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    ResponseEntity<Void> deleteOrder(
            @Parameter(name = "id", description = "Order ID", example = "1") @PathVariable Long id);

    @Operation(
            summary = "generates mock orders",
            description =
                    "Asynchronously triggers sample order generation in the background and immediately returns HTTP 200 with an acknowledgement.",
            tags = {"order-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Order generation initiated",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = GenericResponse.class))
                        }),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    GenericResponse createMockOrders(
            @Parameter(name = "Idempotency-Key", description = "Idempotency key for generation")
                    @RequestHeader(name = "Idempotency-Key", required = true)
                    String idempotencyKey,
            @Parameter(name = "batchSize", description = "Batch size between 1 and 10000")
                    @RequestParam(required = false)
                    @Min(1)
                    @Max(OrderGeneratorService.MAX_GENERATION_BATCH_SIZE)
                    Integer batchSize);

    @Operation(
            summary = "fetches all orders for a customer",
            tags = {"order-controller"},
            responses = {
                @ApiResponse(responseCode = "200", description = "Success"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Bad Request",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
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
                @ApiResponse(responseCode = "200", description = "Success"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Bad Request",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
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
