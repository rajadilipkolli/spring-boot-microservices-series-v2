/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import com.example.catalogservice.model.request.ProductRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Dedicated service responsible for generating mock and benchmark catalog products. */
@Service
public class ProductGeneratorService {

    public static final int MAX_GENERATION_BATCH_SIZE = 10_000;
    private static final int DEFAULT_GENERATION_BATCH_SIZE = 100;

    private record ProductType(
            String name,
            String category,
            String descriptionTemplate,
            BigDecimal minPrice,
            BigDecimal maxPrice) {}

    private static final List<String> DESCRIPTIVE_ADJECTIVES =
            List.of(
                    "Premium",
                    "Compact",
                    "Ergonomic",
                    "Wireless",
                    "Ultra-Durable",
                    "Eco-Friendly",
                    "Smart",
                    "Sleek",
                    "Portable",
                    "High-Performance",
                    "Modern",
                    "Vintage");

    private static final List<String> BRAND_MODIFIERS =
            List.of(
                    "Pro",
                    "Plus",
                    "Elite",
                    "Max",
                    "Prime",
                    "Standard",
                    "Ultra",
                    "Classic",
                    "Essential",
                    "Signature");

    private static final List<ProductType> PRODUCT_TYPES =
            List.of(
                    // Electronics
                    new ProductType(
                            "Noise-Cancelling Headphones",
                            "Electronics",
                            "High-fidelity %s noise-cancelling headphones featuring studio-grade audio, deep bass, and all-day battery life.",
                            BigDecimal.valueOf(99.99),
                            BigDecimal.valueOf(349.99)),
                    new ProductType(
                            "4K Ultra HD Smart TV",
                            "Electronics",
                            "Vibrant %s 4K UHD smart display with HDR support, Dolby Vision, and integrated streaming platforms.",
                            BigDecimal.valueOf(299.99),
                            BigDecimal.valueOf(1199.99)),
                    new ProductType(
                            "Mechanical Gaming Keyboard",
                            "Electronics",
                            "Tactile %s mechanical gaming keyboard with customizable RGB backlighting and hot-swappable switches.",
                            BigDecimal.valueOf(49.99),
                            BigDecimal.valueOf(199.99)),
                    new ProductType(
                            "Wireless Optical Mouse",
                            "Electronics",
                            "Ergonomic %s wireless optical mouse engineered for smooth tracking, silent clicks, and extended battery runtime.",
                            BigDecimal.valueOf(19.99),
                            BigDecimal.valueOf(79.99)),
                    new ProductType(
                            "Bluetooth Soundbar",
                            "Electronics",
                            "Immersive %s Bluetooth soundbar delivering rich cinematic sound and dedicated wireless subwoofer integration.",
                            BigDecimal.valueOf(79.99),
                            BigDecimal.valueOf(299.99)),
                    new ProductType(
                            "Fitness Smartwatch",
                            "Electronics",
                            "Feature-packed %s smartwatch tracking heart rate, sleep metrics, workout activity, and smartphone notifications.",
                            BigDecimal.valueOf(89.99),
                            BigDecimal.valueOf(399.99)),
                    // Accessories
                    new ProductType(
                            "Water-Resistant Backpack",
                            "Accessories",
                            "Spacious %s backpack featuring padded laptop compartments, weatherproof zippers, and ergonomic shoulder straps.",
                            BigDecimal.valueOf(34.99),
                            BigDecimal.valueOf(129.99)),
                    new ProductType(
                            "Polarized Sunglasses",
                            "Accessories",
                            "Stylish %s polarized sunglasses providing complete UV400 protection and scratch-resistant lightweight lenses.",
                            BigDecimal.valueOf(24.99),
                            BigDecimal.valueOf(149.99)),
                    new ProductType(
                            "Genuine Leather Wallet",
                            "Accessories",
                            "Handcrafted %s genuine leather bi-fold wallet equipped with RFID-blocking protection and multiple card slots.",
                            BigDecimal.valueOf(19.99),
                            BigDecimal.valueOf(89.99)),
                    new ProductType(
                            "Stainless Steel Watch",
                            "Accessories",
                            "Timeless %s stainless steel analog wristwatch designed with sapphire crystal glass and 50m water resistance.",
                            BigDecimal.valueOf(59.99),
                            BigDecimal.valueOf(249.99)),
                    new ProductType(
                            "Knitted Winter Scarf",
                            "Accessories",
                            "Warm and breathable %s knitted winter scarf crafted from ultra-soft wool blend fabrics for chilly days.",
                            BigDecimal.valueOf(14.99),
                            BigDecimal.valueOf(49.99)),
                    // Home Goods
                    new ProductType(
                            "Stainless Steel Cookware Set",
                            "Home Goods",
                            "Professional-grade %s tri-ply stainless steel cookware set ensuring even heat distribution and durability.",
                            BigDecimal.valueOf(89.99),
                            BigDecimal.valueOf(299.99)),
                    new ProductType(
                            "Aromatherapy Essential Oil Diffuser",
                            "Home Goods",
                            "Quiet ultrasonic %s essential oil diffuser featuring ambient ambient lighting and automatic safety shut-off.",
                            BigDecimal.valueOf(18.99),
                            BigDecimal.valueOf(59.99)),
                    new ProductType(
                            "Memory Foam Bed Pillow",
                            "Home Goods",
                            "Orthopedic %s memory foam bed pillow engineered for optimal cervical neck support and cooling comfort.",
                            BigDecimal.valueOf(24.99),
                            BigDecimal.valueOf(79.99)),
                    new ProductType(
                            "Ceramic Pour-Over Coffee Maker",
                            "Home Goods",
                            "Artisan %s ceramic pour-over coffee dripper designed for precision temperature stability and balanced brews.",
                            BigDecimal.valueOf(21.99),
                            BigDecimal.valueOf(64.99)),
                    new ProductType(
                            "Dimmable LED Desk Lamp",
                            "Home Goods",
                            "Modern %s dimmable LED desk lamp offering multiple color temperatures, touch controls, and USB charging.",
                            BigDecimal.valueOf(22.99),
                            BigDecimal.valueOf(69.99)));

    private final ProductService productService;

    public ProductGeneratorService(@Lazy ProductService productService) {
        this.productService = productService;
    }

    /**
     * Generates and saves sample products with realistic names, descriptions, varied prices, and
     * working seed images, reusing products with existing codes. New or restored products create
     * outbox events, and product caches are invalidated. Save failures propagate through the
     * returned Mono; Redis deletion failures are suppressed.
     *
     * @param idempotencyKey included in each product code or used to scope generation batches
     * @param batchSize number of products, from 1 through 10,000, or null for 100
     * @return true after all saves complete
     * @throws IllegalArgumentException if a supplied batch size is outside the allowed range
     */
    @Transactional
    public Mono<Boolean> generateAndSaveProducts(String idempotencyKey, Integer batchSize) {
        return generateProducts(idempotencyKey, batchSize)
                .flatMap(productService::saveProduct)
                .then(Mono.just(Boolean.TRUE));
    }

    /**
     * Generates unsaved sample product requests with realistic data, reproducible deterministic
     * variety, prices based on product type, and seed images from Picsum Photos.
     *
     * @param idempotencyKey included in each product code or used for batch reference
     * @param batchSize number of products, from 1 through 10,000, or null for 100
     * @return the generated requests
     * @throws IllegalArgumentException if a supplied batch size is outside the allowed range
     */
    @Transactional
    public Flux<ProductRequest> generateProducts(String idempotencyKey, Integer batchSize) {
        validateBatchSize(batchSize);
        int resolvedBatchSize = batchSize != null ? batchSize : DEFAULT_GENERATION_BATCH_SIZE;

        return Flux.range(0, resolvedBatchSize).map(i -> buildProductRequest(i, idempotencyKey));
    }

    private ProductRequest buildProductRequest(int index, String idempotencyKey) {
        String productCode = String.format("PROD%03d", index);
        ProductType type = PRODUCT_TYPES.get(index % PRODUCT_TYPES.size());
        String adjective =
                DESCRIPTIVE_ADJECTIVES.get(
                        (index / PRODUCT_TYPES.size()) % DESCRIPTIVE_ADJECTIVES.size());
        String modifier = BRAND_MODIFIERS.get((index * 3) % BRAND_MODIFIERS.size());

        String productName = adjective + " " + type.name() + " " + modifier;
        String description = String.format(type.descriptionTemplate(), modifier);
        String imageUrl = "https://picsum.photos/seed/" + productCode + "/300/200";

        BigDecimal price = calculateDeterministicPrice(type, index);

        return new ProductRequest(productCode, productName, description, imageUrl, price);
    }

    private BigDecimal calculateDeterministicPrice(ProductType type, int index) {
        BigDecimal min = type.minPrice();
        BigDecimal max = type.maxPrice();
        BigDecimal range = max.subtract(min);

        // Deterministic offset within the range based on index (0.00 to 0.99)
        int step = (index * 17 + 23) % 100;
        BigDecimal fraction =
                BigDecimal.valueOf(step).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return min.add(range.multiply(fraction)).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Accepts a null batch size for the default or an explicit size from 1 through 10,000.
     *
     * @throws IllegalArgumentException if a supplied size is outside the allowed range
     */
    private static void validateBatchSize(Integer batchSize) {
        if (batchSize != null && (batchSize < 1 || batchSize > MAX_GENERATION_BATCH_SIZE)) {
            throw new IllegalArgumentException(
                    "batchSize must be between 1 and " + MAX_GENERATION_BATCH_SIZE);
        }
    }
}
