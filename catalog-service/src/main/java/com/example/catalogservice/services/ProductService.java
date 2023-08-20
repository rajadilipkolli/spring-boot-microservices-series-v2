/*** Licensed under Apache-2.0 2021-2023 ***/
package com.example.catalogservice.services;

import com.example.catalogservice.entities.Product;
import com.example.catalogservice.exception.ProductNotFoundException;
import com.example.catalogservice.mapper.ProductMapper;
import com.example.catalogservice.model.response.InventoryDto;
import com.example.catalogservice.repositories.ProductRepository;
import com.example.catalogservice.utils.AppConstants;
import com.example.common.dtos.ProductDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.observation.annotation.Observed;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    private final ProductMapper productMapper;

    private final InventoryServiceProxy inventoryServiceProxy;

    private final KafkaTemplate<String, ProductDto> kafkaTemplate;

    @Transactional(readOnly = true)
    public Flux<Product> findAllProducts(String sortBy, String sortDir) {
        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        return this.productRepository
                .findAll(sort)
                .collectList()
                .flatMapMany(
                        products -> {
                            if (products.isEmpty()) {
                                log.info("No Products Exists");
                                return Flux.empty();
                            } else {
                                List<String> productCodeList =
                                        products.stream()
                                                .map(Product::getCode)
                                                .collect(Collectors.toList());

                                return getInventoryByProductCodes(productCodeList)
                                        .collectMap(
                                                InventoryDto::productCode,
                                                InventoryDto::availableQuantity)
                                        .flatMapMany(
                                                inventoriesMap -> {
                                                    products.forEach(
                                                            product -> {
                                                                int availableQuantity =
                                                                        inventoriesMap.getOrDefault(
                                                                                product.getCode(),
                                                                                0);
                                                                product.setInStock(
                                                                        availableQuantity > 0);
                                                            });
                                                    return Flux.fromIterable(products);
                                                });
                            }
                        });
    }

    @CircuitBreaker(
            name = "getInventoryByProductCodes",
            fallbackMethod = "getInventoryByProductCodesFallBack")
    private Flux<InventoryDto> getInventoryByProductCodes(List<String> productCodeList) {
        return inventoryServiceProxy.getInventoryByProductCodes(productCodeList);
    }

    private Flux<InventoryDto> getInventoryByProductCodesFallBack(
            List<String> productCodeList, Exception e) {
        log.error("Exception occurred while fetching product details", e);
        return Flux.empty();
    }

    @Transactional(readOnly = true)
    public Mono<Product> findProductById(Long id) {
        return findProductByProductId(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .flatMap(
                        product ->
                                getInventoryByProductCode(product.getCode())
                                        .map(
                                                inventoryDto -> {
                                                    product.setInStock(
                                                            inventoryDto.availableQuantity() > 0);
                                                    return product;
                                                }));
    }

    @CircuitBreaker(
            name = "getInventoryByProductCode",
            fallbackMethod = "getInventoryByProductCodeFallBack")
    private Mono<InventoryDto> getInventoryByProductCode(String code) {
        return inventoryServiceProxy.getInventoryByProductCode(code);
    }

    private InventoryDto getInventoryByProductCodeFallBack(String code, Throwable e) {
        log.error("Exception occurred while fetching product details", e);
        return new InventoryDto(code, 0);
    }

    @Transactional(readOnly = true)
    public Mono<Product> findProductByProductCode(String productCode) {
        return productRepository.findByCodeAllIgnoreCase(productCode);
    }

    // saves product to db and sends message that new product is available for inventory
    @Observed(name = "product.save", contextualName = "saving-prouduct")
    public Mono<Product> saveProduct(ProductDto productDto) {
        Product product = this.productMapper.toEntity(productDto);
        Mono<Product> persistedProduct = productRepository.save(product);
        this.kafkaTemplate.send(AppConstants.KAFKA_TOPIC, productDto);
        return persistedProduct;
    }

    public Mono<Product> updateProduct(Product product) {
        return productRepository.save(product);
    }

    public Mono<Void> deleteProductById(Long id) {
        return productRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Mono<Boolean> existsProductByProductCode(List<String> productIds) {
        log.info("checking is products Exists :{}", productIds);
        return productRepository
                .countDistinctByCodeAllIgnoreCaseIn(productIds)
                .map(count -> count == productIds.size());
    }

    @Transactional(readOnly = true)
    public Mono<Product> findProductByProductId(Long id) {
        return productRepository.findById(id);
    }
}
