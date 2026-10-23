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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
@Tag(name = "inventory-controller", description = "the inventory-controller API")
public interface InventoryApi {

    @Operation(
            summary = "Returns inventory records using requested pagination and sort options",
            tags = {"inventory-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Successful retrieval",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = PagedResult.class)))
            })
    PagedResult<InventoryResponse> getAllInventories(
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER, required = false)
                    int pageNo,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE, required = false)
                    int pageSize,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY, required = false)
                    String sortBy,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_DIRECTION, required = false)
                    String sortDir);

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
                        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
            })
    ResponseEntity<InventoryResponse> getInventoryByProductCode(
            @PathVariable String productCode, @RequestParam(required = false) Integer delay);

    @Operation(
            summary = "Returns inventory matching supplied product codes",
            tags = {"inventory-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Inventory list retrieved",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = PagedResult.class)))
            })
    ResponseEntity<PagedResult<InventoryResponse>> getInventoryByProductCodes(
            @RequestParam List<String> codes,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER, required = false)
                    int pageNo,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE, required = false)
                    int pageSize,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY, required = false)
                    String sortBy,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_DIRECTION, required = false)
                    String sortDir);

    @Operation(
            summary = "Updates a generated batch using idempotency key",
            tags = {"inventory-controller"},
            responses = {
                @ApiResponse(responseCode = "200", description = "Batch update completed")
            })
    boolean updateInventoryWithRandomValue(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestParam(required = false) @Min(1) @Max(10000) Integer batchSize);

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
                                        schema = @Schema(implementation = InventoryResponse.class)))
            })
    InventoryResponse createInventory(@RequestBody @Valid InventoryRequest inventoryRequest);

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
                        responseCode = "404",
                        description = "Inventory not found",
                        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
            })
    ResponseEntity<InventoryResponse> updateInventory(
            @PathVariable Long id, @RequestBody @Valid InventoryRequest inventoryRequest);

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
                        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE)),
                @ApiResponse(
                        responseCode = "404",
                        description = "Inventory not found",
                        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
            })
    ResponseEntity<InventoryResponse> updateInventoryByProductCode(
            @PathVariable String productCode,
            @RequestBody @Valid InventoryRequest inventoryRequest);

    @Operation(
            summary = "Deletes inventory record by identifier",
            tags = {"inventory-controller"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Inventory deleted",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema =
                                                @Schema(implementation = InventoryResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Inventory not found",
                        content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE))
            })
    ResponseEntity<InventoryResponse> deleteInventory(@PathVariable Long id);
}
