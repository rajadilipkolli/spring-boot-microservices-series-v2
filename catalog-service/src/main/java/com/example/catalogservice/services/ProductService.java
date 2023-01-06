package com.example.catalogservice.services;

import com.example.catalogservice.entities.Product;
import com.example.catalogservice.exception.ProductNotFoundException;
import com.example.catalogservice.mapper.ProductMapper;
import com.example.catalogservice.model.response.PagedResult;
import com.example.catalogservice.repositories.ProductRepository;
import com.example.catalogservice.utils.AppConstants;
import com.example.common.dtos.ProductDto;
import io.micrometer.observation.annotation.Observed;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    private final ProductMapper productMapper;

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

        return new PagedResult<>(productPage);
    }

    @Transactional(readOnly = true)
    public Product findProductById(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
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
}
