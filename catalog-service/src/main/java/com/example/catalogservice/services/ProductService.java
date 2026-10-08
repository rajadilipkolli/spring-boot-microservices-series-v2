/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
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
import io.hypersistence.tsid.TSID;
import io.micrometer.observation.annotation.Observed;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Loggable
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final Duration CACHE_EXPIRY = Duration.ofMinutes(5);
    private static final SecureRandom RAND = new SecureRandom();
    public static final int MAX_GENERATION_BATCH_SIZE = 10_000;
    private static final int DEFAULT_GENERATION_BATCH_SIZE = 101;

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final InventoryServiceProxy inventoryServiceProxy;
    private final OutboxService outboxService;
    private final ReactiveRedisOperations<String, Object> redisOps;

    private final ProductService self;
    private final TSID.Factory tsidFactory;

    public ProductService(
            ProductRepository productRepository,
            ProductMapper productMapper,
            InventoryServiceProxy inventoryServiceProxy,
            OutboxService outboxService,
            ReactiveRedisOperations<String, Object> redisOps,
            @Lazy ProductService self,
            TSID.Factory tsidFactory) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
        this.inventoryServiceProxy = inventoryServiceProxy;
        this.outboxService = outboxService;
        this.redisOps = redisOps;
        this.self = self;
        this.tsidFactory = tsidFactory;
    }

    @Observed(name = "product.findAll", contextualName = "find-all-products")
    public Mono<PagedResult<ProductResponse>> findAllProducts(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        String cacheKey =
                "products:"
                        + pageNo
                        + "_"
                        + pageSize
                        + "_"
                        + sortBy
                        + "_"
                        + sortDir.toLowerCase(Locale.ROOT);

        return cached(cacheKey, () -> fetchAllProductsFromDb(pageNo, pageSize, sortBy, sortDir));
    }

    private Mono<PagedResult<ProductResponse>> fetchAllProductsFromDb(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        Pageable pageable = createPageable(pageNo, pageSize, sortBy, sortDir);

        return productRepository
                .count()
                .flatMap(
                        count -> {
                            if (count == 0) {
                                return Mono.just(
                                        new PagedResult<>(
                                                new PageImpl<>(
                                                        Collections.emptyList(), pageable, 0)));
                            }

                            return productRepository
                                    .findAllBy(pageable)
                                    .collectList()
                                    .flatMap(
                                            products -> {
                                                Flux<ProductResponse> productResponseFlux =
                                                        Flux.fromIterable(products)
                                                                .map(
                                                                        productMapper
                                                                                ::toProductResponse);

                                                return enrichWithAvailability(
                                                        productResponseFlux, pageable, count);
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
        String cacheKey = "product:" + id;
        return cached(
                cacheKey,
                () ->
                        productRepository
                                .findById(id)
                                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                                .map(productMapper::toProductResponse)
                                .flatMap(this::fetchInventoryAndUpdateProductResponse));
    }

    private Mono<InventoryResponse> getInventoryByProductCode(String code) {
        return inventoryServiceProxy.getInventoryByProductCode(code);
    }

    @Observed(name = "product.findByCode", contextualName = "findByProductCode")
    public Mono<ProductResponse> findProductByProductCode(
            String productCode, boolean fetchInStock) {
        String cacheKey = productCodeCacheKey(productCode);

        Mono<ProductResponse> productResponseMono =
                cached(
                        cacheKey,
                        () ->
                                productRepository
                                        .findByProductCodeAllIgnoreCase(productCode)
                                        .map(productMapper::toProductResponse)
                                        .switchIfEmpty(
                                                Mono.error(
                                                        new ProductNotFoundException(
                                                                productCode))));

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
                .switchIfEmpty(Mono.defer(() -> self.createAndSaveProduct(productRequest)))
                // Catch DuplicateKeyException from unique constraint violation
                .onErrorResume(
                        DuplicateKeyException.class,
                        e -> {
                            log.info(
                                    "Concurrent save detected for product code: {}",
                                    productRequest.productCode());
                            // Recovery mechanism: fetch the existing product that was concurrently
                            // saved
                            return productRepository
                                    .findByProductCodeAllIgnoreCase(productRequest.productCode())
                                    .map(productMapper::toProductResponse)
                                    .switchIfEmpty(
                                            // This should never happen, but just in case
                                            Mono.error(
                                                    new ProductAlreadyExistsException(
                                                            productRequest.productCode())));
                        })
                .delayUntil(p -> evictProductCache(p.id(), p.productCode()));
    }

    @Transactional
    public Mono<ProductResponse> createAndSaveProduct(ProductRequest productRequest) {
        return Mono.fromSupplier(
                        () -> {
                            Product product = productMapper.toEntity(productRequest);
                            product.setId(tsidFactory.generate().toLong());
                            product.setNew(true);
                            return product;
                        })
                .flatMap(productRepository::save)
                .flatMap(
                        savedProduct ->
                                outboxService
                                        .createOutboxEvent(
                                                "PRODUCT",
                                                savedProduct.getProductCode(),
                                                "PRODUCT_CREATED",
                                                savedProduct)
                                        .thenReturn(savedProduct))
                .map(productMapper::toProductResponse);
    }

    @Transactional
    @Observed(name = "product.deleteById", contextualName = "deleteProductById")
    public Mono<Void> deleteProductById(Long id) {
        return productRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new ProductNotFoundException(id)))
                .flatMap(
                        product ->
                                productRepository
                                        .deleteById(id)
                                        .then(
                                                outboxService.createOutboxEvent(
                                                        "PRODUCT",
                                                        product.getProductCode(),
                                                        "PRODUCT_DELETED",
                                                        product))
                                        .then(evictProductCache(id, product.getProductCode())));
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

    /**
     * Updates an existing product.
     *
     * @param productRequest the updated product details
     * @param product the existing product entity
     * @return a Mono emitting the updated ProductResponse
     * @throws org.springframework.dao.OptimisticLockingFailureException if the product was updated
     *     concurrently. This exception propagates to the controller to return an HTTP 409 Conflict
     *     response.
     */
    @Transactional
    public Mono<ProductResponse> updateProduct(ProductRequest productRequest, Product product) {
        String previousProductCode = product.getProductCode();
        // Update the post object with data from postRequest
        productMapper.mapProductWithRequest(productRequest, product);

        // Save the updated post object
        return productRepository
                .save(product)
                .flatMap(
                        savedProduct ->
                                outboxService
                                        .createOutboxEvent(
                                                "PRODUCT",
                                                savedProduct.getProductCode(),
                                                "PRODUCT_UPDATED",
                                                savedProduct)
                                        .thenReturn(savedProduct))
                .map(productMapper::toProductResponse)
                .delayUntil(p -> evictProductCache(p.id(), previousProductCode, p.productCode()));
    }

    public Mono<Product> findById(Long id) {
        return this.productRepository.findById(id);
    }

    @Transactional
    public Mono<Boolean> generateProducts(String idempotencyKey, Integer batchSize) {
        validateBatchSize(batchSize);
        int resolvedBatchSize = batchSize != null ? batchSize : DEFAULT_GENERATION_BATCH_SIZE;

        return Flux.range(0, resolvedBatchSize)
                .flatMap(
                        i ->
                                Mono.just(RAND.nextInt(100) + 1)
                                        .map(
                                                randomPrice ->
                                                        new ProductRequest(
                                                                "ProductCode_"
                                                                        + idempotencyKey
                                                                        + "_"
                                                                        + i,
                                                                "Gen Product " + i,
                                                                "Gen Prod Description " + i,
                                                                null,
                                                                (double) randomPrice)))
                .flatMap(this::saveProduct)
                .then(Mono.just(Boolean.TRUE));
    }

    private static void validateBatchSize(Integer batchSize) {
        if (batchSize != null && (batchSize < 1 || batchSize > MAX_GENERATION_BATCH_SIZE)) {
            throw new IllegalArgumentException(
                    "batchSize must be between 1 and " + MAX_GENERATION_BATCH_SIZE);
        }
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

                            return enrichWithAvailability(
                                    productResponseFlux, pageable, (long) products.size());
                        });
    }

    /**
     * Helper method to enrich product responses with availability information from the inventory
     * service. This method centralizes the logic used in both findAllProducts and
     * processSearchResults.
     *
     * @param productResponseFlux Flux of product responses to be enriched
     * @param pageable Pagination information
     * @param totalCount Total count of items for pagination (optional, uses product list size if
     *     null)
     * @return Mono of PagedResult with enriched product responses
     */
    private Mono<PagedResult<ProductResponse>> enrichWithAvailability(
            Flux<ProductResponse> productResponseFlux, Pageable pageable, Long totalCount) {
        return productResponseFlux
                .collectList()
                .flatMap(
                        productResponseList -> {
                            if (productResponseList.isEmpty()) {
                                return Mono.just(
                                        new PagedResult<>(
                                                new PageImpl<>(
                                                        Collections.emptyList(), pageable, 0)));
                            }

                            Flux<String> productCodeFlux =
                                    Flux.fromIterable(productResponseList)
                                            .map(ProductResponse::productCode);

                            return productCodeFlux
                                    .collectList()
                                    .flatMap(
                                            productCodeList ->
                                                    getInventoryByProductCodes(productCodeList)
                                                            .collectMap(
                                                                    InventoryResponse::productCode,
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
                                                                                                                    totalCount
                                                                                                                                    != null
                                                                                                                            ? totalCount
                                                                                                                            : productResponseList
                                                                                                                                    .size())))));
                        });
    }

    private static String productCodeCacheKey(String productCode) {
        return "productCode:" + productCode.toLowerCase(Locale.ROOT);
    }

    private Mono<String> cacheGeneration() {
        return productRepository
                .findCacheGeneration()
                .switchIfEmpty(
                        Mono.error(new IllegalStateException("Missing product cache generation")));
    }

    private <T> Mono<T> cached(String key, Supplier<Mono<T>> source) {
        return Mono.defer(
                () ->
                        cacheGeneration()
                                .flatMap(
                                        generation -> {
                                            String cacheKey = key + ":" + generation;
                                            return this.<T>readCache(cacheKey)
                                                    .switchIfEmpty(
                                                            Mono.defer(source)
                                                                    .flatMap(
                                                                            result ->
                                                                                    writeCache(
                                                                                                    cacheKey,
                                                                                                    result)
                                                                                            .thenReturn(
                                                                                                    result)));
                                        }));
    }

    @SuppressWarnings("unchecked")
    private <T> Mono<T> readCache(String cacheKey) {
        return Mono.defer(() -> redisOps.opsForValue().get(cacheKey))
                .map(value -> (T) value)
                .onErrorResume(
                        ex -> {
                            log.warn("Product cache read failed for {}", cacheKey, ex);
                            return Mono.empty();
                        });
    }

    private Mono<Void> writeCache(String cacheKey, Object result) {
        return Mono.defer(() -> redisOps.opsForValue().set(cacheKey, result, CACHE_EXPIRY))
                .onErrorResume(
                        ex -> {
                            log.warn("Product cache write failed for {}", cacheKey, ex);
                            return Mono.empty();
                        })
                .then();
    }

    private Mono<Void> evictProductCache(Long id, String... productCodes) {
        // Rotate in the product transaction so every node stops using the old entries even
        // when Redis deletion fails. UUIDs also prevent reuse of a rolled-back generation.
        return Mono.defer(
                () ->
                        cacheGeneration()
                                .flatMap(
                                        generation ->
                                                productRepository
                                                        .updateCacheGeneration(
                                                                UUID.randomUUID().toString())
                                                        .filter(updated -> updated == 1)
                                                        .switchIfEmpty(
                                                                Mono.error(
                                                                        new IllegalStateException(
                                                                                "Missing product cache generation")))
                                                        .then(
                                                                deleteCacheEntries(
                                                                        id,
                                                                        productCodes,
                                                                        generation))));
    }

    private Mono<Void> deleteCacheEntries(Long id, String[] productCodes, String generation) {
        String[] keys =
                Stream.concat(
                                Stream.of(productCodes).map(ProductService::productCodeCacheKey),
                                Stream.of("product:" + id))
                        .map(key -> key + ":" + generation)
                        .distinct()
                        .toArray(String[]::new);
        return Mono.defer(() -> redisOps.delete(keys))
                .onErrorResume(
                        ex -> {
                            log.warn(
                                    "Product cache eviction failed; old generation is no longer used",
                                    ex);
                            return Mono.empty();
                        })
                .then();
    }
}
