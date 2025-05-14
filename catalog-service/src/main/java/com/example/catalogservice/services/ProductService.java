/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import com.example.catalogservice.config.logging.Loggable;
import com.example.catalogservice.entities.Product;
import com.example.catalogservice.exception.ProductNotFoundException;
import com.example.catalogservice.kafka.CatalogKafkaProducer;
import com.example.catalogservice.mapper.ProductMapper;
import com.example.catalogservice.model.request.ProductRequest;
import com.example.catalogservice.model.response.InventoryResponse;
import com.example.catalogservice.model.response.PagedResult;
import com.example.catalogservice.model.response.ProductResponse;
import com.example.catalogservice.repositories.ProductRepository;
import io.micrometer.observation.annotation.Observed;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final SecureRandom RAND = new SecureRandom();

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final InventoryServiceProxy inventoryServiceProxy;
    private final CatalogKafkaProducer catalogKafkaProducer;

    public ProductService(
            ProductRepository productRepository,
            ProductMapper productMapper,
            InventoryServiceProxy inventoryServiceProxy,
            CatalogKafkaProducer catalogKafkaProducer) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.inventoryServiceProxy = inventoryServiceProxy;
        this.catalogKafkaProducer = catalogKafkaProducer;
    }

    @Observed(name = "product.findAll", contextualName = "find-all-products")
    public Mono<PagedResult<ProductResponse>> findAllProducts(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        Pageable pageable = createPageable(pageNo, pageSize, sortBy, sortDir);

        Mono<Long> totalProductsCountMono = productRepository.count();
        Flux<Product> pagedProductsFlux = productRepository.findAllBy(pageable);

        return Mono.zip(totalProductsCountMono, pagedProductsFlux.collectList())
                .flatMap(
                        tuple -> {
                            long count = tuple.getT1();
                            List<Product> products = tuple.getT2();

                            if (count == 0) {
                                return Mono.just(
                                        new PagedResult<>(
                                                new PageImpl<>(
                                                        Collections.emptyList(), pageable, 0)));
                            }

                            Flux<ProductResponse> productResponseFlux =
                                    Flux.fromIterable(products)
                                            .map(productMapper::toProductResponse);

                            return productResponseFlux
                                    .collectList()
                                    .flatMap(
                                            productResponseList -> {
                                                Flux<String> productCodeFlux =
                                                        Flux.fromIterable(productResponseList)
                                                                .map(ProductResponse::productCode);

                                                return productCodeFlux
                                                        .collectList()
                                                        .flatMap(
                                                                productCodeList ->
                                                                        getInventoryByProductCodes(
                                                                                        productCodeList)
                                                                                .collectMap(
                                                                                        InventoryResponse
                                                                                                ::productCode,
                                                                                        InventoryResponse
                                                                                                ::availableQuantity)
                                                                                .flatMap(
                                                                                        inventoriesMap ->
                                                                                                updateProductAvailability(
                                                                                                                productResponseList,
                                                                                                                inventoriesMap)
                                                                                                        .collectList()
                                                                                                        .map(
                                                                                                                updatedProducts ->
                                                                                                                        new PagedResult<>(
                                                                                                                                new PageImpl<>(
                                                                                                                                        updatedProducts,
                                                                                                                                        pageable,
                                                                                                                                        count)))));
                                            });
                        });
    }

    private Flux<ProductResponse> updateProductAvailability(
            List<ProductResponse> productResponses, Map<String, Integer> inventoriesMap) {
        return Flux.fromIterable(productResponses)
                .map(
                        productResponse -> {
                            int availableQuantity =
                                    inventoriesMap.getOrDefault(productResponse.productCode(), 0);
                            return productResponse.withInStock(availableQuantity > 0);
                        });
    }

    private Flux<InventoryResponse> getInventoryByProductCodes(List<String> productCodeList) {
        return inventoryServiceProxy.getInventoryByProductCodes(productCodeList);
    }

    @Observed(name = "product.findProductById", contextualName = "findProductById")
    public Mono<ProductResponse> findProductById(Long id) {
        return productRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .map(productMapper::toProductResponse)
                .flatMap(
                        productResponse ->
                                getInventoryByProductCode(productResponse.productCode())
                                        .map(
                                                inventoryDto ->
                                                        productResponse.withInStock(
                                                                inventoryDto.availableQuantity()
                                                                        > 0)));
    }

    private Mono<InventoryResponse> getInventoryByProductCode(String code) {
        return inventoryServiceProxy.getInventoryByProductCode(code);
    }

    @Observed(name = "product.findByCode", contextualName = "findByProductCode")
    public Mono<ProductResponse> findProductByProductCode(
            String productCode, boolean fetchInStock) {
        Mono<ProductResponse> productResponseMono =
                productRepository
                        .findByProductCodeAllIgnoreCase(productCode)
                        .map(productMapper::toProductResponse)
                        .switchIfEmpty(Mono.error(new ProductNotFoundException(productCode)));

        if (fetchInStock) {
            return productResponseMono.flatMap(this::fetchInventoryAndUpdateProductResponse);
        }
        return productResponseMono;
    }

    private Mono<ProductResponse> fetchInventoryAndUpdateProductResponse(
            ProductResponse productResponse) {
        return getInventoryByProductCode(productResponse.productCode())
                .map(
                        inventoryResponse ->
                                productResponse.withInStock(
                                        inventoryResponse.availableQuantity() > 0));
    }

    // saves product to db and sends message that new product is available for inventory
    @Transactional
    @Observed(name = "product.save", contextualName = "saving-product")
    public Mono<ProductResponse> saveProduct(ProductRequest productRequest) {
        // First, check if product already exists - idempotent approach
        return productRepository
                .findByProductCodeAllIgnoreCase(productRequest.productCode())
                .map(productMapper::toProductResponse)
                .switchIfEmpty(createAndSaveProduct(productRequest));
    }

    /** Helper method to create and save a new product */
    private Mono<ProductResponse> createAndSaveProduct(ProductRequest productRequest) {
        return Mono.just(productMapper.toEntity(productRequest))
                .flatMap(productRepository::save)
                .flatMap(
                        savedProduct ->
                                catalogKafkaProducer.send(productRequest).thenReturn(savedProduct))
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
                .countDistinctByProductCodeAllIgnoreCaseIn(productCodes)
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

    @Transactional
    public Mono<Boolean> generateProducts() {
        return Flux.range(0, 101)
                .flatMap(
                        i ->
                                Mono.just(RAND.nextInt(100) + 1)
                                        .map(
                                                randomPrice ->
                                                        new ProductRequest(
                                                                "ProductCode" + i,
                                                                "Gen Product" + i,
                                                                "Gen Prod Description" + i,
                                                                null,
                                                                (double) randomPrice)))
                .flatMap(this::saveProduct)
                .then(Mono.just(Boolean.TRUE));
    }

    public Mono<PagedResult<ProductResponse>> searchProductsByTerm(
            String term, int pageNo, int pageSize, String sortBy, String sortDir) {
        Pageable pageable = createPageable(pageNo, pageSize, sortBy, sortDir);

        Flux<Product> productFlux =
                productRepository
                        .findByProductNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                                term, term, pageable);

        return processSearchResults(productFlux, pageable);
    }

    public Mono<PagedResult<ProductResponse>> searchProductsByPriceRange(
            double minPrice,
            double maxPrice,
            int pageNo,
            int pageSize,
            String sortBy,
            String sortDir) {
        Pageable pageable = createPageable(pageNo, pageSize, sortBy, sortDir);

        Flux<Product> productFlux =
                productRepository.findByPriceBetween(minPrice, maxPrice, pageable);
        return processSearchResults(productFlux, pageable);
    }

    private Pageable createPageable(int pageNo, int pageSize, String sortBy, String sortDir) {
        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();
        return PageRequest.of(pageNo, pageSize, sort);
    }

    public Mono<PagedResult<ProductResponse>> searchProductsByTermAndPriceRange(
            String term,
            double minPrice,
            double maxPrice,
            int pageNo,
            int pageSize,
            String sortBy,
            String sortDir) {
        Pageable pageable = createPageable(pageNo, pageSize, sortBy, sortDir);

        Flux<Product> productFlux =
                productRepository
                        .findByProductNameContainingIgnoreCaseOrDescriptionContainingIgnoreCaseAndPriceBetween(
                                term, term, minPrice, maxPrice, pageable);

        return processSearchResults(productFlux, pageable);
    }

    private Mono<PagedResult<ProductResponse>> processSearchResults(
            Flux<Product> productFlux, Pageable pageable) {
        return productFlux
                .collectList()
                .flatMap(
                        products -> {
                            if (products.isEmpty()) {
                                return Mono.just(
                                        new PagedResult<>(
                                                new PageImpl<>(
                                                        Collections.emptyList(), pageable, 0)));
                            }

                            Flux<ProductResponse> productResponseFlux =
                                    Flux.fromIterable(products)
                                            .map(productMapper::toProductResponse);

                            return productResponseFlux
                                    .collectList()
                                    .flatMap(
                                            productResponseList -> {
                                                Flux<String> productCodeFlux =
                                                        Flux.fromIterable(productResponseList)
                                                                .map(ProductResponse::productCode);

                                                return productCodeFlux
                                                        .collectList()
                                                        .flatMap(
                                                                productCodeList ->
                                                                        getInventoryByProductCodes(
                                                                                        productCodeList)
                                                                                .collectMap(
                                                                                        InventoryResponse
                                                                                                ::productCode,
                                                                                        InventoryResponse
                                                                                                ::availableQuantity)
                                                                                .flatMap(
                                                                                        inventoriesMap ->
                                                                                                updateProductAvailability(
                                                                                                                productResponseList,
                                                                                                                inventoriesMap)
                                                                                                        .collectList()
                                                                                                        .map(
                                                                                                                updatedProducts ->
                                                                                                                        new PagedResult<>(
                                                                                                                                new PageImpl<>(
                                                                                                                                        updatedProducts,
                                                                                                                                        pageable,
                                                                                                                                        products
                                                                                                                                                .size())))));
                                            });
                        });
    }

    /**
     * Find a product by product code if it exists, without throwing an exception Used to make
     * createProduct idempotent
     *
     * @param productCode the product code to search for
     * @return a Mono containing the product if found, empty Mono otherwise
     */
    public Mono<ProductResponse> findByProductCodeIfExists(String productCode) {
        return productRepository
                .findByProductCodeAllIgnoreCase(productCode)
                .map(productMapper::toProductResponse);
    }
}
