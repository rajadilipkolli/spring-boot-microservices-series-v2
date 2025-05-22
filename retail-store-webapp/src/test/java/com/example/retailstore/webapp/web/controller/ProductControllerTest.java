package com.example.retailstore.webapp.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.retailstore.webapp.clients.PagedResult;
import com.example.retailstore.webapp.clients.catalog.CatalogServiceClient;
import com.example.retailstore.webapp.clients.catalog.ProductRequest;
import com.example.retailstore.webapp.clients.catalog.ProductResponse;
import com.example.retailstore.webapp.config.TestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientException;

@WebMvcTest(ProductController.class)
@Import(TestSecurityConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CatalogServiceClient catalogService;

    @Test
    @WithMockUser
    void shouldReturnProductsPage() throws Exception {
        mockMvc.perform(get("/products").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("products"));
    }

    @Test
    @WithMockUser
    void shouldReturnProductsList() throws Exception {
        // Arrange
        ProductResponse product = new ProductResponse(1L, "SKU123", "Test Product", "Description", null, 10.0, true);
        PagedResult<ProductResponse> pagedResult =
                new PagedResult<>(Collections.singletonList(product), 1L, 0, 1, true, true, false, false);
        when(catalogService.getProducts(0)).thenReturn(pagedResult);

        // Act & Assert
        mockMvc.perform(get("/api/products").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(pagedResult)));
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenProductsFetchFails() throws Exception {
        // Arrange
        when(catalogService.getProducts(0)).thenThrow(new RestClientException("Service unavailable"));

        // Act & Assert
        mockMvc.perform(get("/api/products").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Failed to fetch products: Service unavailable"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldCreateProductWhenAdmin() throws Exception {
        // Arrange
        ProductRequest request = new ProductRequest("SKU123", "Test Product", "Description", null, 10.0);
        ProductResponse response = new ProductResponse(1L, "SKU123", "Test Product", "Description", null, 10.0, true);
        when(catalogService.createProduct(any())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
    }

    @Test
    @WithMockUser
    void shouldReturn403WhenNonAdminCreatesProduct() throws Exception {
        // Arrange
        ProductRequest request = new ProductRequest("SKU123", "Test Product", "Description", null, 10.0);

        // Act & Assert
        mockMvc.perform(post("/api/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldReturn400WhenProductCreationFails() throws Exception {
        // Arrange
        ProductRequest request = new ProductRequest("SKU123", "Test Product", "Description", null, 10.0);
        when(catalogService.createProduct(any())).thenThrow(new RestClientException("Service unavailable"));

        // Act & Assert
        mockMvc.perform(post("/api/products")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
