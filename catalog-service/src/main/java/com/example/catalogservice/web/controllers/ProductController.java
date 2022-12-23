package com.example.catalogservice.web.controllers;

import com.example.catalogservice.dtos.ProductDto;
import com.example.catalogservice.entities.Product;
import com.example.catalogservice.services.ProductService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<Product> getAllProducts() {
        return productService.findAllProducts();
    }

    @GetMapping("/id/{id}")
    //  @Retry(name = "product-api", fallbackMethod = "hardcodedResponse")
    //  @CircuitBreaker(name = "default", fallbackMethod = "hardcodedResponse")
    //  @RateLimiter(name="default")
    @Bulkhead(name = "product-api")
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
    @ResponseStatus(HttpStatus.CREATED)
    public Product createProduct(@RequestBody @Validated ProductDto productDto) {
        return productService.saveProduct(productDto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id, @RequestBody Product product) {
        return Optional.of(productService.findProductById(id))
                .map(
                        catalogObj -> {
                            product.setId(id);
                            return ResponseEntity.ok(productService.updateProduct(product));
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Product> deleteProduct(@PathVariable Long id) {
        return Optional.of(productService.findProductById(id))
                .map(
                        catalog -> {
                            productService.deleteProductById(id);
                            return ResponseEntity.ok(catalog);
                        })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
