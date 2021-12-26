package com.example.catalogservice.web.controllers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.catalogservice.dtos.ProductDto;
import com.example.catalogservice.entities.Product;
import com.example.catalogservice.services.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.zalando.problem.jackson.ProblemModule;
import org.zalando.problem.violations.ConstraintViolationProblemModule;

@WebMvcTest(controllers = ProductController.class)
@ActiveProfiles("test")
class ProductControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ProductService productService;

    @Autowired private ObjectMapper objectMapper;

    private List<Product> productList;

    @BeforeEach
    void setUp() {
        this.productList = new ArrayList<>();
        this.productList.add(new Product(1L, "code 1", "name 1", "description 1", 9.0, true));
        this.productList.add(new Product(2L, "code 2", "name 2", "description 2", 10.0, true));
        this.productList.add(new Product(3L, "code 3", "name 3", "description 3", 11.0, true));

        objectMapper.registerModule(new ProblemModule());
        objectMapper.registerModule(new ConstraintViolationProblemModule());
    }

    @Test
    void shouldFetchAllProducts() throws Exception {
        given(productService.findAllProducts()).willReturn(this.productList);

        this.mockMvc
                .perform(get("/api/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(productList.size())));
    }

    @Test
    void shouldFindProductById() throws Exception {
        Long productId = 1L;
        Product product = new Product(productId, "code 1", "name 1", "description 1", 9.0, true);
        given(productService.findProductById(productId)).willReturn(Optional.of(product));

        this.mockMvc
                .perform(get("/api/catalog/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(product.getCode())))
                .andExpect(jsonPath("$.name", is(product.getProductName())))
                .andExpect(jsonPath("$.description", is(product.getDescription())))
                .andExpect(jsonPath("$.price", is(product.getPrice())));
    }

    @Test
    void shouldReturn404WhenFetchingNonExistingProduct() throws Exception {
        Long productId = 1L;
        given(productService.findProductById(productId)).willReturn(Optional.empty());

        this.mockMvc.perform(get("/api/catalog/{id}", productId)).andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateProduct() throws Exception {
        Product product = new Product(1L, "code 1", "name 1", "description 1", 9.0, true);
        given(productService.saveProduct(any(ProductDto.class))).willReturn(product);

        ProductDto productDto = new ProductDto("code 1", "name 1", "description 1", 9.0);
        this.mockMvc
                .perform(
                        post("/api/catalog")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(productDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.code", is(productDto.getCode())));
    }

    @Test
    void shouldReturn400WhenCreateNewProductWithoutCode() throws Exception {
        ProductDto productDto = new ProductDto(null, null, null, 9.0);

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
        Long productId = 1L;
        Product product = new Product(1L, "code 1", "Updated name", "description 1", 9.0, true);
        given(productService.findProductById(productId)).willReturn(Optional.of(product));
        given(productService.updateProduct(any(Product.class)))
                .willAnswer((invocation) -> invocation.getArgument(0));

        this.mockMvc
                .perform(
                        put("/api/catalog/{id}", product.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is(product.getProductName())));
    }

    @Test
    void shouldReturn404WhenUpdatingNonExistingProduct() throws Exception {
        Long productId = 1L;
        given(productService.findProductById(productId)).willReturn(Optional.empty());
        Product product =
                new Product(productId, "code 1", "Updated name", "description 1", 9.0, true);

        this.mockMvc
                .perform(
                        put("/api/catalog/{id}", productId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteProduct() throws Exception {
        Long productId = 1L;
        Product product = new Product(1L, "code 1", "Updated name", "description 1", 9.0, true);
        given(productService.findProductById(productId)).willReturn(Optional.of(product));
        doNothing().when(productService).deleteProductById(product.getId());

        this.mockMvc
                .perform(delete("/api/catalog/{id}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code", is(product.getCode())));
    }

    @Test
    void shouldReturn404WhenDeletingNonExistingProduct() throws Exception {
        Long productId = 1L;
        given(productService.findProductById(productId)).willReturn(Optional.empty());

        this.mockMvc
                .perform(delete("/api/catalog/{id}", productId))
                .andExpect(status().isNotFound());
    }
}
