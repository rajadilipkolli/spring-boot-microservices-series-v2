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
import java.math.BigDecimal;
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

    /**
     * Creates the product service with shared caching and transactional product creation.
     *
     * @param self the Spring proxy used to apply transactions when creating a product
     */
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

    /**
     * Returns a product page with availability, caching the entire result for five minutes. Redis
     * failures are tolerated; database, missing-generation, and unhandled inventory errors are
     * emitted by the returned Mono.
     *
     * @param pageNo the zero-based page index, at least zero
     * @param pageSize the maximum number of products per page, greater than zero
     * @param sortBy the product property to sort by
     * @param sortDir ascending for "asc" (case-insensitive), descending otherwise
     * @return the cached or loaded page; invalid pagination emits IllegalArgumentException on a
     *     cache miss
     */
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

    /**
     * Loads a page and enriches it with inventory availability without consulting Redis. Missing
     * inventory entries are treated as out of stock. Database and unhandled inventory errors
     * propagate through the returned Mono.
     *
     * @param pageNo the zero-based page index
     * @return the enriched page, or an empty page when no products are returned
     * @throws IllegalArgumentException if the page index is negative, the page size is not
     *     positive, or the sort property is empty
     */
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

    /**
     * Finds a product by ID, caching its details and inventory availability for five minutes. Redis
     * failures are tolerated; database and unhandled inventory errors propagate.
     *
     * @return the product, or an error with ProductNotFoundException if absent on a cache miss or
     *     IllegalStateException if the shared cache generation is missing
     */
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

    /**
     * Finds a product by case-insensitive code, caching its details for five minutes. Redis
     * failures are tolerated; database and unhandled inventory errors propagate.
     *
     * @param fetchInStock whether to refresh availability from inventory, including on cache hits;
     *     otherwise availability remains false
     * @return the product, or an error with ProductNotFoundException if absent on a cache miss or
     *     IllegalStateException if the shared cache generation is missing
     */
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

    /**
     * Returns an existing product with the same case-insensitive code, or creates it with a product
     * creation outbox event. Invalidates product caches before emitting either result. Redis
     * deletion failures are suppressed; database, outbox serialization, and cache-generation errors
     * propagate.
     *
     * @return the existing or created product; a duplicate-key race is recovered by looking up the
     *     existing product, or emits ProductAlreadyExistsException if that lookup is empty
     */
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

    /**
     * Deletes a product, records its deletion in the outbox, and invalidates product caches. Redis
     * deletion failures are suppressed; database, outbox serialization, and cache-generation errors
     * propagate.
     *
     * @return completion after deletion and invalidation, or ProductNotFoundException as an error
     *     if the product does not exist
     */
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
     * Updates the supplied product in place, saves it, records an update outbox event, and
     * invalidates product caches, including entries for both the previous and current codes. Redis
     * deletion failures are suppressed; database, outbox serialization, and cache-generation errors
     * propagate through the returned Mono.
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

    /**
     * Creates sample products with whole-unit prices from 1.00 through 100.00, reusing products
     * with existing codes. New products create outbox events, and product caches are invalidated.
     * Save failures propagate through the returned Mono; Redis deletion failures are suppressed.
     *
     * @param idempotencyKey included in each product code with its zero-based batch index;
     *     repeating a key reuses matching codes
     * @param batchSize number of products, from 1 through 10,000, or null for 101
     * @return true after all saves complete
     * @throws IllegalArgumentException if a supplied batch size is outside the allowed range
     */
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
                                                                BigDecimal.valueOf(randomPrice)
                                                                        .setScale(2))))
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

    /**
     * Returns a product page within the inclusive price range, enriched with inventory
     * availability. Missing inventory entries, including those omitted by the inventory fallback,
     * are out of stock. Database and unhandled inventory errors propagate through the returned
     * Mono.
     *
     * @param minPrice inclusive lower unit-price bound
     * @param maxPrice inclusive upper unit-price bound
     * @param pageNo zero-based page index
     * @param pageSize maximum number of products to return, greater than zero
     * @param sortBy product property to sort by
     * @param sortDir ascending for "asc" (case-insensitive), descending otherwise
     * @return the matching page; pagination totals use the fetched page size rather than a count of
     *     all matches
     * @throws IllegalArgumentException if the page index is negative, the page size is not
     *     positive, or the sort property is empty
     */
    public Mono<PagedResult<ProductResponse>> searchProductsByPriceRange(
            BigDecimal minPrice,
            BigDecimal maxPrice,
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

    /**
     * Returns a page of case-insensitive name matches, or description matches within the inclusive
     * price range, enriched with inventory availability. Name matches are not price-filtered.
     * Missing inventory entries, including those omitted by the inventory fallback, are out of
     * stock. Database and unhandled inventory errors propagate through the returned Mono.
     *
     * @param term text to match in the product name or description
     * @param minPrice inclusive lower unit-price bound
     * @param maxPrice inclusive upper unit-price bound
     * @param pageNo zero-based page index
     * @param pageSize maximum number of products to return, greater than zero
     * @param sortBy product property to sort by
     * @param sortDir ascending for "asc" (case-insensitive), descending otherwise
     * @return the matching page; pagination totals use the fetched page size rather than a count of
     *     all matches
     * @throws IllegalArgumentException if the page index is negative, the page size is not
     *     positive, or the sort property is empty
     */
    public Mono<PagedResult<ProductResponse>> searchProductsByTermAndPriceRange(
            String term,
            BigDecimal minPrice,
            BigDecimal maxPrice,
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

    /** Builds a generation-free cache key using locale-independent lowercase product codes. */
    private static String productCodeCacheKey(String productCode) {
        return "productCode:" + productCode.toLowerCase(Locale.ROOT);
    }

    /**
     * Reads the shared product cache generation.
     *
     * @return the generation, or IllegalStateException as an error if it is missing; database
     *     errors propagate
     */
    private Mono<String> cacheGeneration() {
        return productRepository
                .findCacheGeneration()
                .switchIfEmpty(
                        Mono.error(new IllegalStateException("Missing product cache generation")));
    }

    /**
     * Reads the current generation on each subscription and loads from the source on a cache miss.
     * Redis read failures count as misses, and write failures do not discard source results.
     * Generation lookup and source errors propagate; empty results and errors are not cached.
     *
     * @param key the cache key without its generation suffix
     * @param source supplies the value on a miss; emitted values are cached for five minutes
     * @return the cached value or the source result
     */
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

    /**
     * Reads a value using a key that includes its generation suffix.
     *
     * @return the cached value, or an empty Mono for a missing entry or Redis read failure
     */
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

    /**
     * Attempts to cache a value for five minutes using a key that includes its generation suffix.
     * Completes without a value and suppresses Redis write errors.
     */
    private Mono<Void> writeCache(String cacheKey, Object result) {
        return Mono.defer(() -> redisOps.opsForValue().set(cacheKey, result, CACHE_EXPIRY))
                .onErrorResume(
                        ex -> {
                            log.warn("Product cache write failed for {}", cacheKey, ex);
                            return Mono.empty();
                        })
                .then();
    }

    /**
     * Rotates the shared database generation in the caller's transaction to invalidate all product
     * caches, then attempts to delete this product's entries in the previous generation. Redis
     * deletion errors are suppressed; database errors propagate.
     *
     * @param productCodes the current and any previous product codes whose entries should be
     *     deleted
     * @return completion after invalidation, or IllegalStateException as an error if the generation
     *     is missing or its update does not affect exactly one row
     */
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

    /**
     * Attempts to delete the ID and code cache entries for a product, suppressing Redis errors.
     * Page entries remain until expiration.
     *
     * @param generation the generation whose entries should be deleted
     * @return completion without a value, even if Redis deletion fails
     */
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
