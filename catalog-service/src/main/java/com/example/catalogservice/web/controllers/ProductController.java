package com.example.catalogservice.web.controllers;

import com.example.catalogservice.entities.Product;
import com.example.catalogservice.model.response.PagedResult;
import com.example.catalogservice.services.ProductService;
import com.example.catalogservice.utils.AppConstants;
import com.example.common.dtos.ProductDto;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public PagedResult<Product> getAllPosts(
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
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findProductById(id));
    }

    @GetMapping("/productCode/{productCode}")
    public ResponseEntity<Product> getProductByProductCode(@PathVariable String productCode) {
        return productService
                .findProductByProductCode(productCode)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/exists/{productIds}")
    public ResponseEntity<Boolean> existsProductByProductCode(
            @PathVariable List<String> productIds) {
        return ResponseEntity.ok(productService.existsProductByProductCode(productIds));
    }

    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody @Validated ProductDto productDto) {
        Product product = productService.saveProduct(productDto);
        return ResponseEntity.created(URI.create("/api/catalog/id/" + product.getId()))
                .body(product);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id, @RequestBody Product product) {
        return productService
                .findProductByProductId(id)
                .map(
                        catalogObj -> {
                            product.setId(id);
                            return productService.updateProduct(product);
                        })
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Product> deleteProduct(@PathVariable Long id) {
        return productService
                .findProductByProductId(id)
                .map(
                        catalog -> {
                            productService.deleteProductById(id);
                            return catalog;
                        })
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
