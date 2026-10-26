/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.api;

import com.example.common.dto.PagedResult;
import com.example.inventoryservice.inventory.domain.models.InventoryRequest;
import com.example.inventoryservice.inventory.domain.models.InventoryResponse;
import com.example.inventoryservice.inventory.utils.AppConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
@Tag(name = "inventory-controller", description = "the inventory-controller API")
public interface InventorySwaggerApi {

    /**
     * Returns a sorted inventory page with pagination metadata using one-based page numbers. Lookup
     * and sort-resolution failures propagate.
     *
     * @param pageNo zero-based page index
     * @param pageSize maximum records per page; must be positive
     * @param sortBy inventory property to sort by
     * @param sortDir {@code asc} (case-insensitive) for ascending order; otherwise descending
     * @throws IllegalArgumentException if the page index is negative or the page size is not
     *     positive
     */
    @Operation(
            summary = "Returns inventory records using requested pagination and sort options",
            tags = {"inventory-controller"},
            responses = {@ApiResponse(responseCode = "200", description = "Successful retrieval")})
    PagedResult<InventoryResponse> getAllInventories(
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
     * Returns HTTP 200 with the matching inventory, or HTTP 404 if the product code is absent.
     *
     * @param delay optional pause in seconds before lookup; null or non-positive values do not
     *     pause the lookup. Interruption restores the thread's interrupt flag and lookup continues.
     */
    @Operation(
            summary = "Returns inventory by product code",
            tags = {"inventory-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Inventory found",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema =
                                                @Schema(implementation = InventoryResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Inventory not found",
                        content = @Content)
            })
    ResponseEntity<InventoryResponse> getInventoryByProductCode(
            @Parameter(name = "productCode", description = "Product code", example = "P001")
                    @PathVariable
                    String productCode,
            @Parameter(name = "delay", description = "Simulated delay in seconds before lookup")
                    @RequestParam(required = false)
                    Integer delay);

    /**
     * Returns HTTP 200 with a sorted inventory page restricted to the supplied product codes.
     * Result metadata uses one-based page numbers. Lookup and sort-resolution failures propagate.
     *
     * @param pageNo zero-based page index
     * @param pageSize maximum records per page; must be positive
     * @param sortBy inventory property to sort by
     * @param sortDir {@code asc} (case-insensitive) for ascending order; otherwise descending
     * @throws IllegalArgumentException if the page index is negative or the page size is not
     *     positive
     */
    @Operation(
            summary = "Returns inventory matching supplied product codes",
            tags = {"inventory-controller"},
            responses = {
                @ApiResponse(responseCode = "200", description = "Inventory list retrieved")
            })
    ResponseEntity<PagedResult<InventoryResponse>> getInventoryByProductCodes(
            @Parameter(name = "codes", description = "List of product codes") @RequestParam
                    List<String> codes,
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
     * Updates existing inventory with generated product codes to random available quantities from 1
     * through 10,000. Missing products and optimistic locking failures are skipped; other
     * persistence failures propagate.
     *
     * @param idempotencyKey identifies a batch; keys already seen by this service instance skip
     *     updates, including keys retained after a failed attempt
     * @param batchSize number of product codes to try, from 1 through 10,000; null uses 101
     * @return true when processing completes, including when the key was already seen
     * @throws IllegalArgumentException if a supplied batch size is outside the allowed range
     */
    @Operation(
            summary = "Updates a generated batch using idempotency key",
            tags = {"inventory-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Batch update completed",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(type = "boolean"))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    boolean updateInventoryWithRandomValue(
            @Parameter(name = "Idempotency-Key", description = "Idempotency key for batch update")
                    @RequestHeader(name = "Idempotency-Key")
                    String idempotencyKey,
            @Parameter(name = "batchSize", description = "Batch size between 1 and 10000")
                    @RequestParam(required = false)
                    @Min(1)
                    @Max(10000)
                    Integer batchSize);

    /**
     * Creates inventory or restores deleted inventory from a validated request and returns its
     * representation with HTTP 201. Active duplicate product codes raise {@link
     * com.example.inventoryservice.inventory.exception.ProductAlreadyExistsException}; other
     * persistence failures propagate.
     */
    @Operation(
            summary = "Creates new inventory record",
            tags = {"inventory-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Inventory created",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema =
                                                @Schema(implementation = InventoryResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid request content",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Inventory already exists for product code",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    InventoryResponse createInventory(@RequestBody @Valid InventoryRequest inventoryRequest);

    /**
     * Updates inventory by identifier and returns its representation with HTTP 200, or HTTP 404
     * when absent. Persistence failures propagate.
     */
    @Operation(
            summary = "Updates existing inventory record by identifier",
            tags = {"inventory-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Inventory updated",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema =
                                                @Schema(implementation = InventoryResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid request content",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Inventory not found",
                        content = @Content)
            })
    ResponseEntity<InventoryResponse> updateInventory(
            @Parameter(name = "id", description = "Inventory ID", example = "1") @PathVariable
                    Long id,
            @RequestBody @Valid InventoryRequest inventoryRequest);

    /**
     * Updates inventory by product code and returns its representation with HTTP 200, or HTTP 404
     * when absent. A non-null request product code that differs from the path returns HTTP 400.
     * Persistence failures propagate.
     */
    @Operation(
            summary = "Updates existing inventory record by product code",
            tags = {"inventory-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Inventory updated",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema =
                                                @Schema(implementation = InventoryResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Bad request",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Inventory not found",
                        content = @Content)
            })
    ResponseEntity<InventoryResponse> updateInventoryByProductCode(
            @Parameter(name = "productCode", description = "Product code", example = "P001")
                    @PathVariable
                    String productCode,
            @RequestBody @Valid InventoryRequest inventoryRequest);

    /**
     * Deletes inventory by identifier and returns an empty HTTP 200 response, or HTTP 410 when
     * absent. Persistence failures propagate.
     */
    @Operation(
            summary = "Deletes inventory record by identifier",
            tags = {"inventory-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Inventory deleted",
                        content = @Content),
                @ApiResponse(
                        responseCode = "410",
                        description = "Inventory absent or already deleted",
                        content = @Content)
            })
    ResponseEntity<InventoryResponse> deleteInventory(
            @Parameter(name = "id", description = "Inventory ID", example = "1") @PathVariable
                    Long id);
}
