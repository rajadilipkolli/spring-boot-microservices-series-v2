package com.example.catalogservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.catalogservice.common.AbstractIntegrationTest;
import com.example.catalogservice.dtos.ProductDto;
import com.example.catalogservice.entities.Product;
import com.example.catalogservice.repositories.ProductRepository;
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
        productRepository.deleteAll();

        this.productList = new ArrayList<>();
        this.productList.add(new Product(1L, "code 1", "name 1", "description 1", 9.0, true));
        this.productList.add(new Product(2L, "code 2", "name 2", "description 2", 10.0, true));
        this.productList.add(new Product(3L, "code 3", "name 3", "description 3", 11.0, true));
        productList = productRepository.saveAll(productList);
    }

    @Test
    void shouldFetchAllProducts() throws Exception {
        this.mockMvc
                .perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(productList.size())));
    }

    @Test
    void shouldFindProductById() throws Exception {
        Product product = productList.get(0);
        Long productId = product.getId();

        this.mockMvc
                .perform(get("/api/catalog/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(product.getCode())));
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
                .andExpect(jsonPath("$.code", is(productDto.getCode())));
    }

    @Test
    void shouldReturn400WhenCreateNewProductWithoutCode() throws Exception {
        ProductDto productDto = new ProductDto(null, null, null, 0);

        this.mockMvc
                .perform(
                        post("/api/catalog")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(productDto)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", is("application/problem+json")))
                .andExpect(
                        jsonPath(
                                "$.type",
                                is("https://zalando.github.io/problem/constraint-violation")))
                .andExpect(jsonPath("$.title", is("Constraint Violation")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.violations", hasSize(1)))
                .andExpect(jsonPath("$.violations[0].field", is("code")))
                .andExpect(jsonPath("$.violations[0].message", is("Product code can't be blank")))
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
                .andExpect(jsonPath("$.description", is(product.getDescription())));
    }

    @Test
    void shouldDeleteProduct() throws Exception {
        Product product = productList.get(0);

        this.mockMvc
                .perform(delete("/api/catalog/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(product.getCode())));
    }
}
