/*** Licensed under Apache-2.0 2021-2023 ***/
package com.example.catalogservice.services;

import com.example.catalogservice.entities.Product;
import com.example.catalogservice.exception.ProductNotFoundException;
import com.example.catalogservice.mapper.ProductMapper;
import com.example.catalogservice.model.response.InventoryDto;
import com.example.catalogservice.model.response.PagedResult;
import com.example.catalogservice.repositories.ProductRepository;
import com.example.catalogservice.utils.AppConstants;
import com.example.common.dtos.ProductDto;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.observation.annotation.Observed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    public PagedResult<Product> findAllProducts(
            int pageNo, int pageSize, String sortBy, String sortDir) {
        Sort sort =
                sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                        ? Sort.by(sortBy).ascending()
                        : Sort.by(sortBy).descending();

        // create Pageable instance
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<Product> productPage = productRepository.findAll(pageable);

        var productCodeList = productPage.getContent().stream().map(Product::getCode).toList();
        if (!productCodeList.isEmpty()) {
            Map<String, Integer> inventoriesMap =
                    getInventoryByProductCodes(productCodeList).stream()
                            .collect(
                                    Collectors.toMap(
                                            InventoryDto::productCode,
                                            InventoryDto::availableQuantity));

            productPage
                    .getContent()
                    .forEach(
                            product ->
                                    product.setInStock(
                                            inventoriesMap.getOrDefault(product.getCode(), 0) > 0));
        }

        return new PagedResult<>(productPage);
    }

    @CircuitBreaker(
            name = "getInventoryByProductCodes",
            fallbackMethod = "getInventoryByProductCodesFallBack")
    private List<InventoryDto> getInventoryByProductCodes(List<String> productCodeList) {
        return inventoryServiceProxy.getInventoryByProductCodes(productCodeList);
    }

    private List<InventoryDto> getInventoryByProductCodesFallBack(
            List<String> productCodeList, Exception e) {
        log.error("Exception occurred while fetching product details", e);
        return new ArrayList<>();
    }

    @Transactional(readOnly = true)
    public Product findProductById(Long id) {
        return findProductByProductId(id)
                .map(
                        product -> {
                            var inventoryDto = getInventoryByProductCode(product.getCode());
                            product.setInStock(inventoryDto.availableQuantity() > 0);
                            return product;
                        })
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @CircuitBreaker(
            name = "getInventoryByProductCode",
            fallbackMethod = "getInventoryByProductCodeFallBack")
    private InventoryDto getInventoryByProductCode(String code) {
        return inventoryServiceProxy.getInventoryByProductCode(code);
    }

    private InventoryDto getInventoryByProductCodeFallBack(String code, Throwable e) {
        log.error("Exception occurred while fetching product details", e);
        return new InventoryDto(code, 0);
    }

    @Transactional(readOnly = true)
    public Optional<Product> findProductByProductCode(String productCode) {
        return productRepository.findByCodeAllIgnoreCase(productCode);
    }

    // saves product to db and sends message that new product is available for inventory
    @Observed(name = "product.save", contextualName = "saving-prouduct")
    public Product saveProduct(ProductDto productDto) {
        Product product = this.productMapper.toEntity(productDto);
        Product persistedProduct = productRepository.save(product);
        this.kafkaTemplate.send(AppConstants.KAFKA_TOPIC, productDto);
        return persistedProduct;
    }

    public Product updateProduct(Product product) {
        return productRepository.save(product);
    }

    public void deleteProductById(Long id) {
        productRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public boolean existsProductByProductCode(List<String> productIds) {
        long count = productRepository.countDistinctByCodeAllIgnoreCaseIn(productIds);
        return count == productIds.size();
    }

    @Transactional(readOnly = true)
    public Optional<Product> findProductByProductId(Long id) {
        return productRepository.findById(id);
    }
}
