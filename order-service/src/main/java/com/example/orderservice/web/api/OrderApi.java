/***
<p>
    Licensed under MIT License Copyright (c) 2023-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.web.api;

import com.example.orderservice.model.dtos.OrderDto;
import com.example.orderservice.model.response.OrderResponse;
import com.example.orderservice.model.response.PagedResult;
import com.example.orderservice.utils.AppConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
@Tag(name = "order-controller", description = "the order-controller API")
public interface OrderApi {

    @Operation(
            summary = "fetches all orders from kafka Streams",
            tags = {"order-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Success",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = OrderDto.class))
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
    List<OrderDto> all(
            @Parameter(
                            name = "pageNo",
                            example = AppConstants.DEFAULT_PAGE_SIZE,
                            in = ParameterIn.QUERY)
                    int pageNo,
            @Parameter(
                            name = "pageSize",
                            example = AppConstants.DEFAULT_PAGE_SIZE,
                            in = ParameterIn.QUERY)
                    int pageSize);

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
}
