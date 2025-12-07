package com.example.retailstore.webapp.web.controller;

import com.example.retailstore.webapp.clients.PagedResult;
import com.example.retailstore.webapp.clients.catalog.CatalogServiceClient;
import com.example.retailstore.webapp.clients.catalog.ProductRequest;
import com.example.retailstore.webapp.clients.catalog.ProductResponse;
import com.example.retailstore.webapp.exception.InvalidRequestException;
import jakarta.validation.Valid;
import java.util.ArrayList;
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
            var root = objectMapper.readTree(json);
            var dataNode = root.path("data");
            var list = new ArrayList<ProductResponse>();
            if (dataNode.isArray()) {
                for (var elem : dataNode) {
                    Long id = elem.path("id").isNumber() ? elem.path("id").asLong() : null;
                    String productCode = elem.path("productCode").isMissingNode()
                            ? null
                            : elem.path("productCode").asText(null);
                    String productName = elem.path("productName").isMissingNode()
                            ? null
                            : elem.path("productName").asText(null);
                    String description = elem.path("description").isMissingNode()
                            ? null
                            : elem.path("description").asText(null);
                    String imageUrl = elem.path("imageUrl").isMissingNode()
                                    || elem.path("imageUrl").isNull()
                            ? null
                            : elem.path("imageUrl").asText(null);
                    double price = elem.has("price") && elem.path("price").isNumber()
                            ? elem.path("price").asDouble(0.0)
                            : 0.0;
                    boolean inStock =
                            elem.has("inStock") && elem.path("inStock").isBoolean()
                                    ? elem.path("inStock").asBoolean(false)
                                    : false;
                    list.add(new ProductResponse(id, productCode, productName, description, imageUrl, price, inStock));
                }
            }

            long totalElements = root.path("totalElements").isNumber()
                    ? root.path("totalElements").asLong()
                    : 0L;
            int pageNumber =
                    root.path("pageNumber").isNumber() ? root.path("pageNumber").asInt() : 0;
            int totalPages =
                    root.path("totalPages").isNumber() ? root.path("totalPages").asInt() : 0;
            boolean isFirst = root.path("isFirst").asBoolean(false);
            boolean isLast = root.path("isLast").asBoolean(false);
            boolean hasNext = root.path("hasNext").asBoolean(false);
            boolean hasPrevious = root.path("hasPrevious").asBoolean(false);

            return new PagedResult<>(
                    list, totalElements, pageNumber, totalPages, isFirst, isLast, hasNext, hasPrevious);
        } catch (Exception e) {
            log.error("Error fetching products: {}", e.getMessage());
            throw new InvalidRequestException("Failed to fetch products: " + e.getMessage());
        }
    }
}
