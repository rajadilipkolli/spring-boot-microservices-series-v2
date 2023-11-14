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
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.InventoryResponse;
import com.example.catalogservice.model.response.PagedResult;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.catalogservice.repositories.ProductRepository;
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
@Transactional(readOnly = true)
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

                            List<ProductResponse> productResponseList =
                                    count == 0
                                            ? Collections.emptyList()
                                            : tuple.getT2().stream()
                                                    .map(productMapper::toProductResponse)
                                                    .toList();

                            if (count == 0) {
                                return Mono.just(
                                        new PagedResult<>(
                                                new PageImpl<>(
                                                        productResponseList, pageable, count)));
                            }

                            List<String> productCodeList =
                                    productResponseList.stream()
                                            .map(ProductResponse::code)
                                            .toList();

                            return getInventoryByProductCodes(productCodeList)
                                    .collectMap(
                                            InventoryResponse::productCode,
                                            InventoryResponse::availableQuantity)
                                    .map(
                                            inventoriesMap -> {
                                                List<ProductResponse> updatedProducts =
                                                        updateProductAvailability(
                                                                productResponseList,
                                                                inventoriesMap);
                                                return new PagedResult<>(
                                                        new PageImpl<>(
                                                                updatedProducts, pageable, count));
                                            });
                        });
    }

    private List<ProductResponse> updateProductAvailability(
            List<ProductResponse> productResponses, Map<String, Integer> inventoriesMap) {
        return productResponses.stream()
                .map(
                        productResponse -> {
                            int availableQuantity =
                                    inventoriesMap.getOrDefault(productResponse.code(), 0);
                            return productResponse.withInStock(availableQuantity > 0);
                        })
                .toList();
    }

    private Flux<InventoryResponse> getInventoryByProductCodes(List<String> productCodeList) {
        return inventoryServiceProxy.getInventoryByProductCodes(productCodeList);
    }

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

    private Mono<InventoryResponse> getInventoryByProductCode(String code) {
        return inventoryServiceProxy.getInventoryByProductCode(code);
    }

    @Observed(name = "product.findByCode", contextualName = "findByProductCode")
    public Mono<ProductResponse> findProductByProductCode(String productCode) {
        return productRepository
                .findByCodeAllIgnoreCase(productCode)
                .map(productMapper::toProductResponse)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(productCode)));
    }

    // saves product to db and sends message that new product is available for inventory
    @Transactional
    @Observed(name = "product.save", contextualName = "saving-product")
    public Mono<ProductResponse> saveProduct(ProductRequest productRequest) {
        return Mono.just(this.productMapper.toEntity(productRequest))
                .flatMap(productRepository::save)
                .map(
                        savedProduct -> {
                            streamBridge.send(
                                    "inventory-out-0",
                                    this.productMapper.toProductDto(productRequest));
                            return savedProduct;
                        })
                .onErrorResume(
                        DuplicateKeyException.class,
                        e ->
                                // Handle unique key constraint violation here
                                Mono.error(
                                        new ProductAlreadyExistsException(productRequest.code())))
                .map(productMapper::toProductResponse);
    }

    @Transactional
    @Observed(name = "product.deleteById", contextualName = "deleteProductById")
    public Mono<Void> deleteProductById(Long id) {
        return productRepository.deleteById(id);
    }

    public Mono<Boolean> productExistsByProductCodes(List<String> productCodes) {
        log.info("checking if products Exists :{}", productCodes);
        return productRepository
                .countDistinctByCodeAllIgnoreCaseIn(productCodes)
                .map(count -> count == productCodes.size());
    }

    @Observed(name = "product.findById", contextualName = "findById")
    public Mono<ProductResponse> findByIdWithMapping(Long id) {
        return findById(id).map(productMapper::toProductResponse);
    }

    @Transactional
    public Mono<ProductResponse> updateProduct(ProductRequest productRequest, Product product) {
        // Update the post object with data from postRequest
        productMapper.mapProductWithRequest(productRequest, product);

        // Save the updated post object
        return productRepository.save(product).map(productMapper::toProductResponse);
    }

    public Mono<Product> findById(Long id) {
        return this.productRepository.findById(id);
    }
}
