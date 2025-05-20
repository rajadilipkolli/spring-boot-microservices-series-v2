/***
<p>
    Licensed under MIT License Copyright (c) 2024 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.catalogservice.entities.Product;
import com.example.catalogservice.kafka.CatalogKafkaProducer;
import com.example.catalogservice.mapper.ProductMapper;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.catalogservice.repositories.ProductRepository;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductMapper productMapper;

    @Mock private ProductRepository productRepository;

    @Mock private CatalogKafkaProducer catalogKafkaProducer;

    @InjectMocks private ProductService productService;

    @Captor private ArgumentCaptor<ProductRequest> productCaptor;

    @Test
    void testGenerateProducts() {
        // Stubbing productMapper.toEntity()
        given(productMapper.toEntity(any(ProductRequest.class)))
                .willAnswer(
                        invocation -> {
                            ProductRequest request = invocation.getArgument(0);
                            int randomPrice = ThreadLocalRandom.current().nextInt(1, 101);
                            return new Product()
                                    .setId(1L)
                                    .setProductCode(request.productCode())
                                    .setProductName(request.productName())
                                    .setDescription(request.description())
                                    .setPrice(randomPrice);
                        });

        // Stubbing productMapper.toProductResponse()
        given(productMapper.toProductResponse(any(Product.class)))
                .willAnswer(
                        invocationOnMock -> {
                            Product product = invocationOnMock.getArgument(0);
                            return new ProductResponse(
                                    product.getId(),
                                    product.getProductCode(),
                                    product.getProductName(),
                                    product.getDescription(),
                                    null,
                                    product.getPrice(),
                                    true);
                        });

        given(catalogKafkaProducer.send(any(ProductRequest.class))).willReturn(Mono.just(true));

        // Mock the repository findByProductCodeAllIgnoreCase method to return empty Mono
        // This is needed for the idempotency check in saveProduct
        given(productRepository.findByProductCodeAllIgnoreCase(any(String.class)))
                .willReturn(Mono.empty());

        // Stubbing productRepository.save()
        given(productRepository.save(any(Product.class))).willReturn(Mono.just(new Product()));

        // Use StepVerifier to test the method
        StepVerifier.create(productService.generateProducts())
                .expectSubscription()
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        // Assert that each product's price is within the expected range
        List<ProductRequest> capturedProducts = productCaptor.getAllValues();
        assertTrue(
                capturedProducts.stream()
                        .allMatch(product -> product.price() >= 1 && product.price() <= 100));
    }
}
