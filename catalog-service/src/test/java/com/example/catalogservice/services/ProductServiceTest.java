/***
<p>
    Licensed under MIT License Copyright (c) 2024 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;

import com.example.catalogservice.entities.Product;
import com.example.catalogservice.mapper.ProductMapper;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.repositories.ProductRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.stream.function.StreamBridge;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductMapper productMapper;
    @Mock private StreamBridge streamBridge;

    @InjectMocks private ProductService productService;

    @Captor private ArgumentCaptor<Product> productCaptor;

    @Test
    void testGenerateProducts() {

        given(productMapper.toEntity(any(ProductRequest.class)))
                .willReturn(new Product(1L, "code", "name", "description", 10D));

        productService.generateProducts();

        // Assert that each product's price is within the expected range
        List<Product> capturedProducts = productCaptor.getAllValues();
        assertTrue(
                capturedProducts.stream()
                        .allMatch(product -> product.getPrice() >= 1 && product.getPrice() <= 100));
    }
}
