/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.catalogservice.web.controllers;

import com.example.catalogservice.config.logging.Loggable;
import com.example.catalogservice.exception.ProductNotFoundException;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.PagedResult;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.catalogservice.services.ProductService;
import com.example.catalogservice.utils.AppConstants;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/catalog")
@Loggable
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public Mono<PagedResult<ProductResponse>> getAllProducts(
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_NUMBER, required = false)
                    int pageNo,
            @RequestParam(defaultValue = AppConstants.DEFAULT_PAGE_SIZE, required = false)
                    int pageSize,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_BY, required = false)
                    String sortBy,
            @RequestParam(defaultValue = AppConstants.DEFAULT_SORT_DIRECTION, required = false)
                    String sortDir) {
        return productService.findAllProducts(pageNo, pageSize, sortBy, sortDir);
    }

    @GetMapping("/id/{id}")
    //  @Retry(name = "product-api", fallbackMethod = "hardcodedResponse")
    //  @CircuitBreaker(name = "default", fallbackMethod = "hardcodedResponse")
    //  @RateLimiter(name="default")
    //  @Bulkhead(name = "product-api")
    public Mono<ResponseEntity<ProductResponse>> getProductById(@PathVariable Long id) {
        return productService.findProductById(id).map(ResponseEntity::ok);
    }

    @GetMapping("/productCode/{productCode}")
    public Mono<ResponseEntity<ProductResponse>> getProductByProductCode(
            @PathVariable String productCode,
            @RequestParam(required = false) boolean fetchInStock) {
        return productService
                .findProductByProductCode(productCode, fetchInStock)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/exists")
    public Mono<ResponseEntity<Boolean>> productExistsByProductCodes(
            @RequestParam List<String> productCodes) {
        return productService.productExistsByProductCodes(productCodes).map(ResponseEntity::ok);
    }

    @GetMapping("/generate")
    public Mono<Boolean> createRandomProducts() {
        return productService.generateProducts();
    }

    @PostMapping
    public Mono<ResponseEntity<ProductResponse>> createProduct(
            @RequestBody @Valid ProductRequest productRequest) {
        return productService
                .saveProduct(productRequest)
                .map(
                        productResponse ->
                                ResponseEntity.created(
                                                URI.create(
                                                        "/api/catalog/id/" + productResponse.id()))
                                        .body(productResponse));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ProductResponse>> updateProduct(
            @PathVariable Long id, @RequestBody ProductRequest productRequest) {
        return productService
                .findById(id)
                .flatMap(
                        product ->
                                productService
                                        .updateProduct(productRequest, product)
                                        .map(ResponseEntity::ok))
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<ProductResponse>> deleteProduct(@PathVariable Long id) {
        return productService
                .findByIdWithMapping(id)
                .flatMap(
                        product ->
                                productService
                                        .deleteProductById(id)
                                        .then(Mono.just(ResponseEntity.ok(product))))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }
}
