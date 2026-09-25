/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.orderservice.services;

import com.example.orderservice.config.logging.Loggable;
import com.example.orderservice.entities.Order;
import com.example.orderservice.entities.OrderStatus;
import com.example.orderservice.exception.ProductNotFoundException;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.model.dtos.OrderDto;
import com.example.orderservice.model.request.OrderItemRequest;
import com.example.orderservice.model.request.OrderRequest;
import com.example.orderservice.model.response.OrderResponse;
import com.example.orderservice.model.response.PagedResult;
import com.example.orderservice.repositories.OrderRepository;
import com.example.orderservice.utils.LogSanitizer;
import io.micrometer.observation.annotation.Observed;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.jobrunr.jobs.annotations.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
@Loggable
@Observed(name = "orderService")
public class OrderService {

    private static final int PRODUCT_VALIDATION_BATCH_SIZE = 25;
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final CatalogService catalogService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    /**
     * Creates an order service with its persistence, mapping, catalog, event, and transaction
     * collaborators.
     *
     * @param orderRepository repository used to access orders
     * @param orderMapper mapper between order API models and entities
     * @param catalogService service used to validate products
     * @param eventPublisher publisher for persisted order events
     * @param transactionTemplate template used to run persistence operations in a transaction
     */
    public OrderService(
            OrderRepository orderRepository,
            OrderMapper orderMapper,
            CatalogService catalogService,
            ApplicationEventPublisher eventPublisher,
            TransactionTemplate transactionTemplate) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.catalogService = catalogService;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Finds a page of orders using the requested sort order.
     *
     * @param pageNo zero-based page number
     * @param pageSize number of orders per page
     * @param sortBy order property to sort by
     * @param sortDir sort direction
     * @return the requested page of order responses
     */
    public PagedResult<OrderResponse> findAllOrders(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();

        // create Pageable instance
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        // Error:: JpaSystem firstResult/maxResults specified with collection fetch. In memory
        // pagination was about to be applied. Failing because 'Fail on pagination over collection
        // fetch' is enabled.
        // To fix above error Fetches only ParentEntities ids and then using keys fetch Data.
        Page<Long> page = orderRepository.findAllOrders(pageable);
        return getOrderResponsePagedResult(page);
    }

    /**
     * Finds an order by its identifier.
     *
     * @param id order identifier
     * @return the order, or an empty optional if it does not exist
     */
    public Optional<Order> findOrderById(Long id) {
        return orderRepository.findOrderById(id);
    }

    /**
     * Validates and saves an order, then publishes its persisted representation.
     *
     * @param orderRequest order to save
     * @return the persisted order response
     * @throws ProductNotFoundException if a requested product does not exist or is out of stock
     */
    public OrderResponse saveOrder(OrderRequest orderRequest) {
        // Verify if items exists
        List<String> productCodes =
                orderRequest.items().stream()
                        .map(OrderItemRequest::productCode)
                        .map(String::toUpperCase)
                        .toList();
        if (productsExistsAndInStock(productCodes).exists()) {
            log.debug(
                    "ProductCodes :{} exists in db, hence proceeding",
                    LogSanitizer.sanitizeCollection(productCodes));
            return transactionTemplate.execute(_ -> persistOrder(orderRequest));
        } else {
            log.debug(
                    "one or more of product codes :{} does not exists in db",
                    LogSanitizer.sanitizeCollection(productCodes));
            throw new ProductNotFoundException(productCodes);
        }
    }

    /**
     * Persists an order and publishes its persisted representation.
     *
     * @param orderRequest order to persist
     * @return the persisted order response
     */
    private OrderResponse persistOrder(OrderRequest orderRequest) {
        Order orderEntity = this.orderMapper.orderRequestToEntity(orderRequest);
        Order savedOrder = this.orderRepository.save(orderEntity);
        OrderDto persistedOrderDto = this.orderMapper.toDto(savedOrder);
        // Should send persistedOrderDto as it contains OrderId used for subsequent processing
        eventPublisher.publishEvent(persistedOrderDto);
        return this.orderMapper.toResponse(savedOrder);
    }

    /**
     * Validates and saves a batch of orders, then publishes each persisted order.
     *
     * @param orderRequests orders to save
     * @return the persisted order responses
     * @throws ProductNotFoundException if a requested product does not exist or is out of stock
     */
    public List<OrderResponse> saveBatchOrders(List<OrderRequest> orderRequests) {
        // Collect all product codes to validate
        List<String> allProductCodes =
                orderRequests.stream()
                        .flatMap(order -> order.items().stream())
                        .map(OrderItemRequest::productCode)
                        .map(String::toUpperCase)
                        .distinct()
                        .toList();
        // TODO once Catalog-service migrates to QUERY HttpMethod we should change below
        // implementation to validate all product codes in a single call instead of batch processing
        boolean allProductsExist =
                IntStream.iterate(
                                0,
                                index -> index < allProductCodes.size(),
                                index -> index + PRODUCT_VALIDATION_BATCH_SIZE)
                        .mapToObj(
                                start ->
                                        allProductCodes.subList(
                                                start,
                                                Math.min(
                                                        start + PRODUCT_VALIDATION_BATCH_SIZE,
                                                        allProductCodes.size())))
                        .allMatch(productBatch -> productsExistsAndInStock(productBatch).exists());

        if (allProductsExist) {
            log.debug(
                    "All ProductCodes exist in db, proceeding with batch save: {}",
                    LogSanitizer.sanitizeCollection(allProductCodes));
            return transactionTemplate.execute(_ -> persistBatchOrders(orderRequests));
        } else {
            log.debug(
                    "One or more product codes do not exist in db: {}",
                    LogSanitizer.sanitizeCollection(allProductCodes));
            throw new ProductNotFoundException(allProductCodes);
        }
    }

    /**
     * Persists a batch of orders and publishes each persisted representation.
     *
     * @param orderRequests orders to persist
     * @return the persisted order responses
     */
    private List<OrderResponse> persistBatchOrders(List<OrderRequest> orderRequests) {
        List<Order> orderEntities =
                orderRequests.stream().map(this.orderMapper::orderRequestToEntity).toList();

        List<Order> savedOrders = this.orderRepository.saveAll(orderEntities);

        // Publish an OrderCreatedEvent per saved order; Kafka dispatch happens in
        // OrderEventPublisher
        savedOrders.forEach(
                order -> {
                    OrderDto dto = orderMapper.toDto(order);
                    eventPublisher.publishEvent(dto);
                });

        return savedOrders.stream().map(this.orderMapper::toResponse).toList();
    }

    /**
     * Checks whether the requested products exist and are in stock.
     *
     * @param productIds product identifiers to check
     * @return the catalog product-existence response
     */
    private CatalogServiceProxy.ProductExistsResponse productsExistsAndInStock(
            List<String> productIds) {
        return catalogService.productsExistsByCodes(productIds);
    }

    @Transactional
    public void deleteOrderById(Long id) {
        orderRepository.deleteById(id);
    }

    @Transactional
    public OrderResponse updateOrder(OrderRequest orderRequest, Order orderObj) {
        this.orderMapper.updateOrderFromOrderRequest(orderRequest, orderObj);
        Order persistedOrder = this.orderRepository.save(orderObj);
        return this.orderMapper.toResponse(persistedOrder);
    }

    public Optional<Order> findById(Long id) {
        return orderRepository.findById(id);
    }

    public Optional<OrderResponse> findOrderByIdAsResponse(Long id) {
        return orderRepository.findOrderById(id).map(this.orderMapper::toResponse);
    }

    public PagedResult<OrderResponse> getOrdersByCustomerId(Long customerId, Pageable pageable) {
        // Error:: JpaSystem firstResult/maxResults specified with collection fetch. In memory
        // pagination was about to be applied. Failing because 'Fail on pagination over collection
        // fetch' is enabled.
        // To fix above error Fetches only ParentEntities ids and then using keys fetch Data.
        Page<Long> page = orderRepository.findAllOrdersByCustomerId(customerId, pageable);
        // fetching parentAlongWithChildEntries
        return getOrderResponsePagedResult(page);
    }

    private PagedResult<OrderResponse> getOrderResponsePagedResult(Page<Long> page) {
        // fetching parent along With ChildEntries
        List<Order> ordersWithOrderItems = orderRepository.findByIdIn(page.getContent());
        // Mapping Order to OrderDTO CompletableFuture
        List<CompletableFuture<OrderResponse>> completableFutureList =
                ordersWithOrderItems.stream()
                        .map(
                                order ->
                                        CompletableFuture.supplyAsync(
                                                () -> this.orderMapper.toResponse(order)))
                        .toList();
        // Joining all completable Future to get DTOs
        List<OrderResponse> orderListDto =
                completableFutureList.stream().map(CompletableFuture::join).toList();
        return new PagedResult<>(
                orderListDto,
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.hasNext(),
                page.hasPrevious());
    }

    @Job(name = "reProcessNewOrders", retries = 2)
    @Transactional
    public void retryNewOrders() {
        // fetch all orders where Status is New in Order
        List<Order> byStatusOrderByIdAsc =
                orderRepository.findByStatusAndLastModifiedDateLessThanOrderByIdAsc(
                        OrderStatus.NEW, LocalDateTime.now().minusMinutes(5));
        byStatusOrderByIdAsc.forEach(
                order -> {
                    try {
                        OrderDto persistedOrderDto = this.orderMapper.toDto(order);
                        log.info(
                                "Retrying Order :{}",
                                LogSanitizer.sanitizeForLog(String.valueOf(persistedOrderDto)));
                        eventPublisher.publishEvent(persistedOrderDto);

                        // Update lastModifiedDate to prevent immediate repolling and endless spam
                        order.setLastModifiedDate(LocalDateTime.now());
                        orderRepository.save(order);
                    } catch (Exception e) {
                        log.error("Failed to retry publishing order :{}", order.getId(), e);
                    }
                });
    }
}
