package com.example.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class ProductDtoTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @ParameterizedTest
    @CsvSource({"10, 10.00", "10.5, 10.50", "10.50, 10.50", "1E+2, 100.00"})
    void serializesPriceAsNumberWithTwoDecimalPlaces(String price, String expectedPrice) {
        ProductDto product =
                new ProductDto("P001", "Product", "Description", new BigDecimal(price));

        String json = jsonMapper.writeValueAsString(product);

        assertThat(json).contains("\"price\":" + expectedPrice + "}");
        assertThat(jsonMapper.readValue(json, ProductDto.class)).isEqualTo(product);
    }

    @Test
    void preservesNullPrice() {
        ProductDto product = new ProductDto("P001", "Product", "Description", null);

        String json = jsonMapper.writeValueAsString(product);

        assertThat(json).contains("\"price\":null");
        assertThat(jsonMapper.readValue(json, ProductDto.class).price()).isNull();
    }

    @Test
    void preservesExcessPrecisionForValidation() {
        BigDecimal price = new BigDecimal("10.001");

        ProductDto product = new ProductDto("P001", "Product", "Description", price);

        assertThat(product.price()).isEqualTo(price);
    }
}
