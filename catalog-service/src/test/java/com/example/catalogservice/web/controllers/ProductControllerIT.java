package com.example.catalogservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.catalogservice.common.AbstractIntegrationTest;
import com.example.catalogservice.entities.Product;
import com.example.catalogservice.repositories.ProductRepository;
import com.example.common.dtos.ProductDto;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class ProductControllerIT extends AbstractIntegrationTest {

    @Autowired private ProductRepository productRepository;

    private List<Product> productList = null;

    @BeforeEach
    void setUp() {
        productRepository.deleteAllInBatch();

        this.productList = new ArrayList<>();
        this.productList.add(new Product(null, "code 1", "name 1", "description 1", 9.0, true));
        this.productList.add(new Product(null, "code 2", "name 2", "description 2", 10.0, true));
        this.productList.add(new Product(null, "code 3", "name 3", "description 3", 11.0, true));
        productList = productRepository.saveAll(productList);
    }

    @Test
    void shouldFetchAllProducts() throws Exception {
        this.mockMvc
                .perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(8)))
                .andExpect(jsonPath("$.data.size()", is(productList.size())))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.pageNumber", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.isFirst", is(true)))
                .andExpect(jsonPath("$.isLast", is(true)))
                .andExpect(jsonPath("$.hasNext", is(false)))
                .andExpect(jsonPath("$.hasPrevious", is(false)));
    }

    @Test
    void shouldFindProductById() throws Exception {
        Product product = productList.get(0);
        Long productId = product.getId();

        this.mockMvc
                .perform(get("/api/catalog/id/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(product.getId()), Long.class))
                .andExpect(jsonPath("$.code", is(product.getCode())))
                .andExpect(jsonPath("$.productName", is(product.getProductName())))
                .andExpect(jsonPath("$.description", is(product.getDescription())))
                .andExpect(jsonPath("$.price", is(product.getPrice())));
    }

    @Test
    void shouldNotFindProductById() throws Exception {
        Long productId = 100L;

        this.mockMvc
                .perform(get("/api/catalog/id/{id}", productId))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.type", is("https://api.microservices.com/errors/not-found")))
                .andExpect(jsonPath("$.title", is("Product Not Found")))
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.detail", is("Product with id 100 not found")))
                .andExpect(jsonPath("$.instance", is("/api/catalog/id/100")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.errorCategory", is("Generic")))
                .andReturn();
    }

    @Test
    void shouldFindProductByProductCode() throws Exception {
        Product product = productList.get(0);

        this.mockMvc
                .perform(get("/api/catalog/productCode/{productCode}", product.getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(product.getId()), Long.class))
                .andExpect(jsonPath("$.code", is(product.getCode())))
                .andExpect(jsonPath("$.productName", is(product.getProductName())))
                .andExpect(jsonPath("$.description", is(product.getDescription())))
                .andExpect(jsonPath("$.price", is(product.getPrice())));
    }

    @Test
    void shouldCreateNewProduct() throws Exception {
        ProductDto productDto = new ProductDto("code 4", "name 4", "description 4", 19.0);
        this.mockMvc
                .perform(
                        post("/api/catalog")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(productDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.code", is(productDto.code())))
                .andExpect(jsonPath("$.productName", is(productDto.productName())))
                .andExpect(jsonPath("$.description", is(productDto.description())))
                .andExpect(jsonPath("$.price", is(productDto.price())))
                .andExpect(header().exists("Location"));
    }

    @Test
    void shouldReturn400WhenCreateNewProductWithoutCode() throws Exception {
        ProductDto productDto = new ProductDto(null, null, null, null);

        this.mockMvc
                .perform(
                        post("/api/catalog")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(productDto)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(jsonPath("$.type", is("about:blank")))
                .andExpect(jsonPath("$.title", is("Bad Request")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                .andExpect(jsonPath("$.instance", is("/api/catalog")))
                .andReturn();
    }

    @Test
    void shouldUpdateProduct() throws Exception {
        Product product = productList.get(0);
        product.setDescription("Updated Catalog");

        this.mockMvc
                .perform(
                        put("/api/catalog/{id}", product.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(product.getId()), Long.class))
                .andExpect(jsonPath("$.code", is(product.getCode())))
                .andExpect(jsonPath("$.productName", is(product.getProductName())))
                .andExpect(jsonPath("$.description", is(product.getDescription())))
                .andExpect(jsonPath("$.price", is(product.getPrice())));
    }

    @Test
    void shouldDeleteProduct() throws Exception {
        Product product = productList.get(0);

        this.mockMvc
                .perform(delete("/api/catalog/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(product.getId()), Long.class))
                .andExpect(jsonPath("$.code", is(product.getCode())))
                .andExpect(jsonPath("$.productName", is(product.getProductName())))
                .andExpect(jsonPath("$.description", is(product.getDescription())))
                .andExpect(jsonPath("$.price", is(product.getPrice())));
    }
}
