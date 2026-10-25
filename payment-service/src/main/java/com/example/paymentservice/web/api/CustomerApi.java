/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.web.api;

import com.example.common.dto.PagedResult;
import com.example.paymentservice.model.request.CustomerRequest;
import com.example.paymentservice.model.response.CustomerResponse;
import com.example.paymentservice.model.response.OrderResponse;
import com.example.paymentservice.utils.AppConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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
            responses = {@ApiResponse(responseCode = "200", description = "Successful retrieval")})
    PagedResult<CustomerResponse> getAllCustomers(
            @Parameter(
                            name = "pageNo",
                            description = "Page number (0-based)",
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
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    ResponseEntity<CustomerResponse> getCustomerById(
            @Parameter(name = "id", description = "Customer ID", example = "1") @PathVariable
                    Long id);

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
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    ResponseEntity<CustomerResponse> getCustomerByName(
            @Parameter(name = "name", description = "Customer name", example = "John Doe")
                    @PathVariable
                    String name);

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
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    ResponseEntity<CustomerResponse> getCustomerByEmail(
            @Parameter(
                            name = "email",
                            description = "Customer email address",
                            example = "john.doe@example.com")
                    @RequestParam
                    String email);

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
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Customer already exists",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
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
                        responseCode = "400",
                        description = "Invalid input",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Customer not found",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    ResponseEntity<CustomerResponse> updateCustomer(
            @Parameter(name = "id", description = "Customer ID", example = "1") @PathVariable
                    Long id,
            @RequestBody @Valid CustomerRequest customerRequest);

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
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    ResponseEntity<CustomerResponse> deleteCustomer(
            @Parameter(name = "id", description = "Customer ID", example = "1") @PathVariable
                    Long id);

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
                @ApiResponse(responseCode = "200", description = "Orders retrieved"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid pagination parameters",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Customer not found",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "503",
                        description = "Order service unavailable",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    ResponseEntity<PagedResult<OrderResponse>> getOrdersByCustomerId(
            @Parameter(name = "id", description = "Customer ID", example = "1") @PathVariable
                    Long id,
            @Parameter(
                            name = "pageNo",
                            description = "Page number (0-based)",
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
