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

    /**
     * Retrieves a sorted customer page with counts and navigation metadata.
     *
     * @param pageNo zero-based page index; defaults to 0 when omitted
     * @param pageSize positive number of customers per page; defaults to 10 when omitted
     * @param sortBy name, email, or amountAvailable; other values sort by ID, the default
     * @param sortDir ascending for {@code asc}, ignoring case; descending otherwise
     * @return the requested page with a one-based page number
     * @throws IllegalArgumentException if the page index is negative or the page size is not
     *     positive
     */
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

    /**
     * Retrieves a customer by identifier.
     *
     * @return HTTP 200 with the matching customer
     * @throws com.example.paymentservice.exception.CustomerNotFoundException if no customer matches
     */
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

    /**
     * Retrieves a customer by an exact name match.
     *
     * @return HTTP 200 with the matching customer
     * @throws com.example.paymentservice.exception.CustomerNotFoundException if no customer matches
     */
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

    /**
     * Retrieves a customer after lowercasing the email using the root locale.
     *
     * @return HTTP 200 with the matching customer
     * @throws com.example.paymentservice.exception.CustomerNotFoundException if no customer matches
     */
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

    /**
     * Creates a customer with a lowercase email address and the requested opening balance.
     *
     * @return HTTP 201 with the saved customer and its resource URI in the Location header
     * @throws com.example.paymentservice.exception.CustomerExistsException if the email already
     *     exists
     * @throws org.springframework.dao.DataIntegrityViolationException if another database integrity
     *     constraint fails
     */
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

    /**
     * Replaces customer contact details and available balance while preserving reserved funds.
     *
     * @return HTTP 200 with the updated customer
     * @throws com.example.paymentservice.exception.CustomerNotFoundException if no customer matches
     * @throws org.springframework.dao.OptimisticLockingFailureException if the customer changes
     *     during the update
     */
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

    /**
     * Deletes a customer after retrieving its details.
     *
     * @return HTTP 200 with the customer details retrieved before deletion
     * @throws com.example.paymentservice.exception.CustomerNotFoundException if no customer matches
     */
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

    /**
     * Retrieves customer orders after validating pagination and confirming the customer exists.
     *
     * @param pageNo zero-based page index; defaults to 0 when omitted
     * @param pageSize positive number of orders per page; defaults to 10 when omitted
     * @param sortBy order property to sort by; defaults to {@code id} when omitted
     * @param sortDir direction forwarded to the order service; defaults to {@code asc} when omitted
     * @return HTTP 200 with the order service's orders and pagination metadata unchanged
     * @throws IllegalArgumentException if the page index is negative or the page size is not
     *     positive
     * @throws com.example.paymentservice.exception.CustomerNotFoundException if no customer matches
     * @throws org.springframework.web.client.RestClientResponseException if the order service
     *     returns an HTTP error
     * @throws org.springframework.web.client.ResourceAccessException if the order request fails due
     *     to an I/O error
     * @throws io.github.resilience4j.circuitbreaker.CallNotPermittedException if the order circuit
     *     breaker rejects the call
     */
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
