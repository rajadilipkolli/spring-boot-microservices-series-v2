package com.example.common.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class ProductDtoTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @ParameterizedTest
    @CsvSource({
        "10, 10.00",
        "10.5, 10.50",
        "10.50, 10.50",
        "1E+2, 100.00",
        "1E+16, 10000000000000000.00",
        "99999999999999999, 99999999999999999.00"
    })
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

    @ParameterizedTest
    @ValueSource(strings = {"10.001", "100000000000000000.001"})
    void preservesExcessPrecisionForValidation(String value) {
        BigDecimal price = new BigDecimal(value);

        ProductDto product = new ProductDto("P001", "Product", "Description", price);

        assertThat(product.price()).isEqualTo(price);
    }

    @ParameterizedTest
    @CsvSource({"100000000000000000, 0", "1, -17", "1, -1000000000", "1, -2147483648"})
    void rejectsTooManyIntegerDigitsBeforeNormalization(long unscaledValue, int scale) {
        BigDecimal price = BigDecimal.valueOf(unscaledValue, scale);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProductDto("P001", "Product", "Description", price))
                .withMessage("Price must have at most 17 integer digits");
    }
}
