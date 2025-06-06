package com.example.retailstore.webapp.web.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.retailstore.webapp.clients.PagedResult;
import com.example.retailstore.webapp.clients.catalog.CatalogServiceClient;
import com.example.retailstore.webapp.clients.catalog.ProductRequest;
import com.example.retailstore.webapp.clients.catalog.ProductResponse;
import com.example.retailstore.webapp.config.TestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

@WebMvcTest(controllers = ProductController.class)
@Import(TestSecurityConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogServiceClient catalogServiceClient;

    @Autowired
    private ObjectMapper objectMapper;

    private List<ProductResponse> productResponseList;
    private PagedResult<ProductResponse> pagedResult;

    @BeforeEach
    void setUp() {
        // Set up test data
        productResponseList = List.of(
                new ProductResponse(1L, "PROD-1", "Test Product 1", "Description 1", "image1.jpg", 10.99, true),
                new ProductResponse(2L, "PROD-2", "Test Product 2", "Description 2", "image2.jpg", 20.99, true),
                new ProductResponse(3L, "PROD-3", "Test Product 3", "Description 3", "image3.jpg", 30.99, true));

        pagedResult = new PagedResult<>(
                productResponseList,
                3L, // Total elements
                0, // Page number
                1, // Total pages
                true, // Is first
                true, // Is last
                false, // Has next
                false // Has previous
                );
    }

    @Test
    @WithMockUser
    void index_shouldRedirectToProductsPage() throws Exception {
        mockMvc.perform(get("/").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products"));
    }

    @Test
    @WithMockUser
    void showProductsPage_shouldRenderProducts() throws Exception {
        mockMvc.perform(get("/products").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("products"))
                .andExpect(model().attribute("pageNo", 0));
    }

    @Test
    @WithMockUser
    void showProductsPage_shouldRenderProductsWithSpecifiedPage() throws Exception {
        int pageNumber = 2;
        mockMvc.perform(get("/products")
                        .param("page", String.valueOf(pageNumber))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("products"))
                .andExpect(model().attribute("pageNo", pageNumber));
    }

    @Test
    @WithMockUser
    void products_shouldReturnPagedResult() throws Exception {
        when(catalogServiceClient.getProducts(anyInt())).thenReturn(pagedResult);

        mockMvc.perform(get("/api/products").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].id", is(1)))
                .andExpect(jsonPath("$.data[0].productCode", is("PROD-1")))
                .andExpect(jsonPath("$.data[0].productName", is("Test Product 1")))
                .andExpect(jsonPath("$.data[0].price", is(10.99)))
                .andExpect(jsonPath("$.data[1].id", is(2)))
                .andExpect(jsonPath("$.data[2].id", is(3)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.pageNumber", is(0)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.isFirst", is(true)))
                .andExpect(jsonPath("$.isLast", is(true)))
                .andExpect(jsonPath("$.hasNext", is(false)))
                .andExpect(jsonPath("$.hasPrevious", is(false)));
    }

    @Test
    @WithMockUser
    void products_shouldReturnPagedResultForSpecifiedPage() throws Exception {
        when(catalogServiceClient.getProducts(2)).thenReturn(pagedResult);

        mockMvc.perform(get("/api/products").param("page", "2").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Test
    @WithMockUser
    void createProduct_shouldReturnCreatedProduct() throws Exception {
        ProductRequest productRequest =
                new ProductRequest("PROD-4", "New Product", "New Description", "image4.jpg", 40.99);
        ProductResponse productResponse =
                new ProductResponse(4L, "PROD-4", "New Product", "New Description", "image4.jpg", 40.99, true);

        when(catalogServiceClient.createProduct(any(ProductRequest.class))).thenReturn(productResponse);

        mockMvc.perform(post("/api/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(4)))
                .andExpect(jsonPath("$.productCode", is("PROD-4")))
                .andExpect(jsonPath("$.productName", is("New Product")))
                .andExpect(jsonPath("$.description", is("New Description")))
                .andExpect(jsonPath("$.price", is(40.99)));
    }

    @Test
    @WithMockUser
    void createProduct_shouldRejectRequestWithoutCsrfToken() throws Exception {
        ProductRequest productRequest =
                new ProductRequest("PROD-4", "New Product", "New Description", "image4.jpg", 40.99);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void products_shouldHandleErrorWhenServiceFails() throws Exception {
        when(catalogServiceClient.getProducts(anyInt()))
                .thenThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        mockMvc.perform(get("/api/products").with(csrf())).andExpect(status().isInternalServerError());
    }
}
