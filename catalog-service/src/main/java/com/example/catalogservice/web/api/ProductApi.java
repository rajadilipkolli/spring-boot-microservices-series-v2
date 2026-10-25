/***
<p>
    Licensed under MIT License Copyright (c) 2025-2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.web.api;

import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.catalogservice.services.ProductGeneratorService;
import com.example.catalogservice.utils.AppConstants;
import com.example.catalogservice.web.controllers.ProductController.GenerateProductsResponse;
import com.example.catalogservice.web.controllers.ProductController.ProductExistsResponse;
import com.example.common.dto.PagedResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

@Tag(name = "product")
public interface ProductApi {

    @Operation(
            summary = "Get all products",
            description = "Retrieves a paginated list of products.",
            tags = {"product"},
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful retrieval of products")
            })
    Mono<PagedResult<ProductResponse>> getAllProducts(
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

    @Operation(
            summary = "Get product by ID",
            description = "Retrieves a single product by its database ID.",
            tags = {"product"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Product found",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ProductResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Product not found",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    Mono<ResponseEntity<ProductResponse>> getProductById(
            @Parameter(name = "id", description = "Product ID", example = "1") @PathVariable
                    Long id);

    @Operation(
            summary = "Get product by code",
            description = "Retrieves a single product by its unique product code.",
            tags = {"product"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Product found",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ProductResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Product not found",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    Mono<ResponseEntity<ProductResponse>> getProductByProductCode(
            @Parameter(name = "productCode", description = "Product code", example = "P001")
                    @PathVariable
                    String productCode,
            @Parameter(
                            name = "fetchInStock",
                            description = "Whether to fetch current inventory stock status")
                    @RequestParam(required = false)
                    Boolean fetchInStock,
            @Parameter(name = "delay", description = "Simulated delay in seconds before response")
                    @RequestParam(required = false)
                    Integer delay);

    @Operation(
            summary = "Check products exist by codes",
            description = "Verifies if products exist for the given list of product codes.",
            tags = {"product"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Product existence checked successfully",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                ProductExistsResponse.class)))
            })
    Mono<ResponseEntity<ProductExistsResponse>> productExistsByProductCodes(
            @Parameter(name = "productCodes", description = "List of product codes to check")
                    @RequestParam
                    List<String> productCodes);

    @Operation(
            summary = "Generate random products",
            description = "Generates and saves sample products in batches.",
            tags = {"product"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Random products generated successfully",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema =
                                                @Schema(
                                                        implementation =
                                                                GenerateProductsResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Validation error",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    Mono<GenerateProductsResponse> createRandomProducts(
            @Parameter(
                            name = "Idempotency-Key",
                            description = "Idempotency key for batch generation")
                    @RequestHeader(name = "Idempotency-Key")
                    String idempotencyKey,
            @Parameter(
                            name = "batchSize",
                            description = "Number of products to generate (between 1 and 10000)")
                    @RequestParam(required = false)
                    @Min(1) @Max(ProductGeneratorService.MAX_GENERATION_BATCH_SIZE) Integer batchSize);

    @Operation(
            summary = "Create product",
            description = "Creates a new product in the catalog.",
            tags = {"product"},
            responses = {
                @ApiResponse(
                        responseCode = "201",
                        description = "Product created successfully",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ProductResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid product request content",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Product code already exists or conflict occurred",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    Mono<ResponseEntity<ProductResponse>> createProduct(
            @RequestBody @Valid ProductRequest productRequest);

    @Operation(
            summary = "Update product",
            description = "Updates an existing product by its ID.",
            tags = {"product"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Product updated successfully",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ProductResponse.class))),
                @ApiResponse(
                        responseCode = "400",
                        description = "Invalid product request content",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Product not found",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class))),
                @ApiResponse(
                        responseCode = "409",
                        description = "Conflict occurred during update",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    Mono<ResponseEntity<ProductResponse>> updateProduct(
            @Parameter(name = "id", description = "Product ID", example = "1") @PathVariable
                    Long id,
            @RequestBody @Valid ProductRequest productRequest);

    @Operation(
            summary = "Delete product",
            description = "Soft-deletes a product by its ID.",
            tags = {"product"},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "Product deleted successfully",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                        schema = @Schema(implementation = ProductResponse.class))),
                @ApiResponse(
                        responseCode = "404",
                        description = "Product not found",
                        content =
                                @Content(
                                        mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                        schema = @Schema(implementation = ProblemDetail.class)))
            })
    Mono<ResponseEntity<ProductResponse>> deleteProduct(
            @Parameter(name = "id", description = "Product ID", example = "1") @PathVariable
                    Long id);

    /**
     * Searches by a nonempty term, a complete price range, or both; returns all products when
     * neither is supplied. A lone price bound is ignored. With both criteria, name matches bypass
     * the price range while description matches must fall within it. Service errors propagate to
     * the caller.
     *
     * @param term case-insensitive name or description text; null or empty disables term filtering
     * @param minPrice inclusive lower unit-price bound, used only when maxPrice is also present
     * @param maxPrice inclusive upper unit-price bound, used only when minPrice is also present
     * @param pageNo zero-based page index
     * @param pageSize maximum number of products per page, greater than zero
     * @param sortBy product property to sort by
     * @param sortDir ascending for "asc" (case-insensitive), descending otherwise
     * @return the product page with inventory availability
     */
    @Operation(
            summary = "Search products by term, price range, or both",
            description = "Allows searching for products based on a text term and/or price range",
            tags = {"product-search"},
            responses = {
                @ApiResponse(responseCode = "200", description = "Successful search operation"),
                @ApiResponse(
                        responseCode = "400",
                        description = "Bad Request",
                        content = {
                            @Content(
                                    mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                                    schema = @Schema(implementation = ProblemDetail.class))
                        })
            })
    Mono<PagedResult<ProductResponse>> searchProducts(
            @Parameter(
                            name = "term",
                            description = "Text to search for in product name or description")
                    @RequestParam(required = false)
                    String term,
            @Parameter(
                            name = "minPrice",
                            description = "Minimum price range for filtering products")
                    @RequestParam(required = false)
                    BigDecimal minPrice,
            @Parameter(
                            name = "maxPrice",
                            description = "Maximum price range for filtering products")
                    @RequestParam(required = false)
                    BigDecimal maxPrice,
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
