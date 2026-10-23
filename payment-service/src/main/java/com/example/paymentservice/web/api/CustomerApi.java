/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.web.api;

import com.example.common.dto.PagedResult;
import com.example.paymentservice.model.request.CustomerRequest;
import com.example.paymentservice.model.response.CustomerResponse;
import com.example.paymentservice.model.response.OrderResponse;
import com.example.paymentservice.utils.AppConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
@Tag(name = "customer-controller", description = "the customer-controller API")
public interface CustomerApi {

    @Operation(
            summary = "Returns paginated customers",
            tags = {"customer-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful retrieval",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = PagedResult.class)))
            })
    PagedResult<CustomerResponse> getAllCustomers(
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER, required = false)
                    int pageNo,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE, required = false)
                    int pageSize,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY, required = false)
                    String sortBy,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_DIRECTION, required = false)
                    String sortDir);

    @Operation(
            summary = "Finds customer by identifier",
            tags = {"customer-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Customer found",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = CustomerResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Customer not found",
                        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
            })
    ResponseEntity<CustomerResponse> getCustomerById(@PathVariable Long id);

    @Operation(
            summary = "Finds customer by name",
            tags = {"customer-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Customer found",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = CustomerResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Customer not found",
                        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
            })
    ResponseEntity<CustomerResponse> getCustomerByName(@PathVariable String name);

    @Operation(
            summary = "Finds customer by email",
            tags = {"customer-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Customer found",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = CustomerResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Customer not found",
                        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
            })
    ResponseEntity<CustomerResponse> getCustomerByEmail(@RequestParam String email);

    @Operation(
            summary = "Creates a new customer",
            tags = {"customer-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Customer created",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = CustomerResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid input",
                        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
            })
    ResponseEntity<CustomerResponse> createCustomer(
            @RequestBody @Valid CustomerRequest customerRequest);

    @Operation(
            summary = "Updates an existing customer",
            tags = {"customer-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Customer updated",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = CustomerResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Customer not found",
                        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
            })
    ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable Long id, @RequestBody @Valid CustomerRequest customerRequest);

    @Operation(
            summary = "Deletes a customer",
            tags = {"customer-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Customer deleted",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = CustomerResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Customer not found",
                        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
            })
    ResponseEntity<CustomerResponse> deleteCustomer(@PathVariable Long id);

    @Operation(
            summary = "Returns customer orders",
            tags = {"customer-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Orders retrieved",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = PagedResult.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Customer not found",
                        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
            })
    ResponseEntity<PagedResult<OrderResponse>> getOrdersByCustomerId(
            @PathVariable Long id,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER, required = false)
                    int pageNo,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE, required = false)
                    int pageSize,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY, required = false)
                    String sortBy,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_DIRECTION, required = false)
                    String sortDir);
}
