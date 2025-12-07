package com.example.retailstore.webapp.web.controller;

import com.example.retailstore.webapp.clients.PagedResult;
import com.example.retailstore.webapp.clients.catalog.CatalogServiceClient;
import com.example.retailstore.webapp.clients.catalog.ProductRequest;
import com.example.retailstore.webapp.clients.catalog.ProductResponse;
import com.example.retailstore.webapp.exception.InvalidRequestException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Controller
class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);
    private final CatalogServiceClient catalogServiceClient;
    private final ObjectMapper objectMapper;

    ProductController(CatalogServiceClient catalogServiceClient, ObjectMapper objectMapper) {
        this.catalogServiceClient = catalogServiceClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    String index() {
        return "redirect:/products";
    }

    @GetMapping("/products")
    String showProductsPage(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("pageNo", page);
        return "products";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/products")
    @ResponseBody
    ProductResponse createProduct(@Valid @RequestBody ProductRequest productRequest) {
        log.info("Creating new product: {}", productRequest);
        try {
            return catalogServiceClient.createProduct(productRequest);
        } catch (Exception e) {
            log.error("Error creating product: {}", e.getMessage());
            throw new InvalidRequestException("Failed to create product: " + e.getMessage());
        }
    }

    @GetMapping("/api/products")
    @ResponseBody
    PagedResult<ProductResponse> products(@RequestParam(defaultValue = "0") int page, Model model) {
        log.info("Fetching products for page: {}", page);
        try {
            String json = catalogServiceClient.getProducts(page);
            return objectMapper.readValue(json, new TypeReference<PagedResult<ProductResponse>>() {});
        } catch (Exception e) {
            log.error("Error fetching products: {}", e.getMessage());
            throw new InvalidRequestException("Failed to fetch products: " + e.getMessage());
        }
    }
}
