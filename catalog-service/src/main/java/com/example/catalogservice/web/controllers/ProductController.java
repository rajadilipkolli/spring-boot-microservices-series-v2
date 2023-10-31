/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.web.controllers;

import com.example.catalogservice.config.logging.Loggable;
import com.example.catalogservice.entities.Product;
import com.example.catalogservice.model.response.PagedResult;
import com.example.catalogservice.services.ProductService;
import com.example.catalogservice.utils.AppConstants;
import com.example.common.dtos.ProductDto;
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
    public Mono<PagedResult<Product>> getAllPosts(
            @RequestParam(
                            value = "pageNo",
                            defaultValue = AppConstants.DEFAULT_PAGE_NUMBER,
                            required = false)
                    int pageNo,
            @RequestParam(
                            value = "pageSize",
                            defaultValue = AppConstants.DEFAULT_PAGE_SIZE,
                            required = false)
                    int pageSize,
            @RequestParam(
                            value = "sortBy",
                            defaultValue = AppConstants.DEFAULT_SORT_BY,
                            required = false)
                    String sortBy,
            @RequestParam(
                            value = "sortDir",
                            defaultValue = AppConstants.DEFAULT_SORT_DIRECTION,
                            required = false)
                    String sortDir) {
        return productService.findAllProducts(pageNo, pageSize, sortBy, sortDir);
    }

    @GetMapping("/id/{id}")
    //  @Retry(name = "product-api", fallbackMethod = "hardcodedResponse")
    //  @CircuitBreaker(name = "default", fallbackMethod = "hardcodedResponse")
    //  @RateLimiter(name="default")
    //  @Bulkhead(name = "product-api")
    public Mono<ResponseEntity<Product>> getProductById(@PathVariable Long id) {
        return productService.findProductById(id).map(ResponseEntity::ok);
    }

    @GetMapping("/productCode/{productCode}")
    public Mono<ResponseEntity<Product>> getProductByProductCode(@PathVariable String productCode) {
        return productService
                .findProductByProductCode(productCode)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/exists")
    public Mono<ResponseEntity<Boolean>> productExistsByProductCodes(
            @RequestParam(name = "productCodes") List<String> productCodes) {
        return productService.productExistsByProductCodes(productCodes).map(ResponseEntity::ok);
    }

    @PostMapping
    public Mono<ResponseEntity<Product>> createProduct(@RequestBody @Valid ProductDto productDto) {
        return productService
                .saveProduct(productDto)
                .map(
                        product ->
                                ResponseEntity.created(
                                                URI.create("/api/catalog/id/" + product.getId()))
                                        .body(product));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<Product>> updateProduct(
            @PathVariable Long id, @RequestBody Product product) {
        return productService
                .findById(id)
                .flatMap(
                        catalogObj -> {
                            product.setId(id);
                            return productService.updateProduct(product).map(ResponseEntity::ok);
                        })
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Product>> deleteProduct(@PathVariable Long id) {
        return productService
                .findById(id)
                .flatMap(
                        product ->
                                productService
                                        .deleteProductById(id)
                                        .then(Mono.just(ResponseEntity.ok(product))))
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()));
    }
}
