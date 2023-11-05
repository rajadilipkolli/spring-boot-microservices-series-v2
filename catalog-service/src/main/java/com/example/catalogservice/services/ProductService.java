/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import com.example.catalogservice.config.logging.Loggable;
import com.example.catalogservice.entities.Product;
import com.example.catalogservice.exception.ProductAlreadyExistsException;
import com.example.catalogservice.exception.ProductNotFoundException;
import com.example.catalogservice.mapper.ProductMapper;
import com.example.catalogservice.model.response.InventoryDto;
import com.example.catalogservice.model.response.PagedResult;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.catalogservice.repositories.ProductRepository;
import com.example.common.dtos.ProductDto;
import io.micrometer.observation.annotation.Observed;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Transactional
@Loggable
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final InventoryServiceProxy inventoryServiceProxy;
    private final StreamBridge streamBridge;

    public ProductService(
            ProductRepository productRepository,
            ProductMapper productMapper,
            InventoryServiceProxy inventoryServiceProxy,
            StreamBridge streamBridge) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.inventoryServiceProxy = inventoryServiceProxy;
        this.streamBridge = streamBridge;
    }

    @Transactional(readOnly = true)
    @Observed(name = "product.findAll", contextualName = "find-all-products")
    public Mono<PagedResult<ProductResponse>> findAllProducts(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);

        Mono<Long> totalProductsCountMono = productRepository.count();
        Mono<List<Product>> pagedProductsMono = productRepository.findAllBy(pageable).collectList();

        return Mono.zip(totalProductsCountMono, pagedProductsMono)
                .flatMap(
                        tuple -> {
                            long count = tuple.getT1();
                            List<Product> products =
                                    count == 0 ? Collections.emptyList() : tuple.getT2();

                            List<ProductResponse> productResponses =
                                    products.isEmpty()
                                            ? Collections.emptyList()
                                            : products.stream()
                                                    .map(productMapper::toProductResponse)
                                                    .toList();

                            if (count == 0) {
                                return Mono.just(
                                        new PagedResult<>(
                                                productResponses,
                                                new PageImpl<>(productResponses, pageable, count)));
                            }

                            List<String> productCodeList =
                                    products.stream().map(Product::getCode).toList();

                            return getInventoryByProductCodes(productCodeList)
                                    .collectMap(
                                            InventoryDto::productCode,
                                            InventoryDto::availableQuantity)
                                    .map(
                                            inventoriesMap -> {
                                                updateProductAvailability(products, inventoriesMap);
                                                List<ProductResponse> productResponseList =
                                                        products.stream()
                                                                .map(
                                                                        productMapper
                                                                                ::toProductResponse)
                                                                .toList();
                                                return new PagedResult<>(
                                                        productResponseList,
                                                        new PageImpl<>(
                                                                productResponseList,
                                                                pageable,
                                                                count));
                                            });
                        });
    }

    private void updateProductAvailability(
            List<Product> products, Map<String, Integer> inventoriesMap) {
        products.forEach(
                product -> {
                    int availableQuantity = inventoriesMap.getOrDefault(product.getCode(), 0);
                    product.setInStock(availableQuantity > 0);
                });
    }

    private Flux<InventoryDto> getInventoryByProductCodes(List<String> productCodeList) {
        return inventoryServiceProxy.getInventoryByProductCodes(productCodeList);
    }

    @Transactional(readOnly = true)
    @Observed(name = "product.findProductById", contextualName = "findProductById")
    public Mono<ProductResponse> findProductById(Long id) {
        return productRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .flatMap(
                        product ->
                                getInventoryByProductCode(product.getCode())
                                        .map(
                                                inventoryDto -> {
                                                    product.setInStock(
                                                            inventoryDto.availableQuantity() > 0);
                                                    return product;
                                                }))
                .map(productMapper::toProductResponse);
    }

    private Mono<InventoryDto> getInventoryByProductCode(String code) {
        return inventoryServiceProxy.getInventoryByProductCode(code);
    }

    @Transactional(readOnly = true)
    @Observed(name = "product.findByCode", contextualName = "findByProductCode")
    public Mono<ProductResponse> findProductByProductCode(String productCode) {
        return productRepository
                .findByCodeAllIgnoreCase(productCode)
                .map(productMapper::toProductResponse);
    }

    // saves product to db and sends message that new product is available for inventory
    @Observed(name = "product.save", contextualName = "saving-product")
    public Mono<ProductResponse> saveProduct(ProductDto productDto) {
        return Mono.just(this.productMapper.toEntity(productDto))
                .flatMap(productRepository::save)
                .map(
                        savedProduct -> {
                            streamBridge.send("inventory-out-0", productDto);
                            return savedProduct;
                        })
                .onErrorResume(
                        DuplicateKeyException.class,
                        e ->
                                // Handle unique key constraint violation here
                                Mono.error(new ProductAlreadyExistsException(productDto.code())))
                .map(productMapper::toProductResponse);
    }

    public Mono<ProductResponse> updateProduct(ProductDto productDto, Long id) {
        Product product = productMapper.toEntity(productDto);
        product.setId(id);
        Mono<Product> savedProduct = productRepository.save(product);
        return savedProduct.map(productMapper::toProductResponse);
    }

    @Observed(name = "product.deleteById", contextualName = "deleteProductById")
    public Mono<Void> deleteProductById(Long id) {
        return productRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Mono<Boolean> productExistsByProductCodes(List<String> productCodes) {
        log.info("checking if products Exists :{}", productCodes);
        return productRepository
                .countDistinctByCodeAllIgnoreCaseIn(productCodes)
                .map(count -> count == productCodes.size());
    }

    @Transactional(readOnly = true)
    @Observed(name = "product.findById", contextualName = "findById")
    public Mono<Product> findById(Long id) {
        return productRepository.findById(id);
    }
}
