/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.json.JsonMapper;

class PagedResultTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void jsonRoundTripHasAllEightPropertyNames() throws Exception {
        Page<String> page = new PageImpl<>(List.of("item1", "item2"), PageRequest.of(0, 2), 5);
        PagedResult<String> original = new PagedResult<>(page);

        String json = jsonMapper.writeValueAsString(original);

        assertThat(json)
                .contains("\"data\":")
                .contains("\"totalElements\":")
                .contains("\"pageNumber\":")
                .contains("\"totalPages\":")
                .contains("\"isFirst\":")
                .contains("\"isLast\":")
                .contains("\"hasNext\":")
                .contains("\"hasPrevious\":");

        PagedResult<String> deserialized =
                jsonMapper.readValue(
                        json,
                        jsonMapper
                                .getTypeFactory()
                                .constructParametricType(PagedResult.class, String.class));
        assertThat(deserialized.data()).containsExactly("item1", "item2");
        assertThat(deserialized.totalElements()).isEqualTo(5);
        assertThat(deserialized.pageNumber()).isEqualTo(1);
        assertThat(deserialized.totalPages()).isEqualTo(3);
        assertThat(deserialized.isFirst()).isTrue();
        assertThat(deserialized.isLast()).isFalse();
        assertThat(deserialized.hasNext()).isTrue();
        assertThat(deserialized.hasPrevious()).isFalse();
    }

    @Test
    void pageConstructorsSetOneBasedPageNumber() {
        Page<Integer> page = new PageImpl<>(List.of(10, 20), PageRequest.of(2, 2), 10);

        PagedResult<Integer> fromPage = new PagedResult<>(page);
        assertThat(fromPage.pageNumber()).isEqualTo(3);
        assertThat(fromPage.data()).containsExactly(10, 20);

        PagedResult<String> fromPageAndData =
                new PagedResult<>(page, List.of("mapped10", "mapped20"));
        assertThat(fromPageAndData.pageNumber()).isEqualTo(3);
        assertThat(fromPageAndData.data()).containsExactly("mapped10", "mapped20");

        PagedResult<String> fromDataAndPage =
                new PagedResult<>(List.of("mapped10", "mapped20"), page);
        assertThat(fromDataAndPage.pageNumber()).isEqualTo(3);
        assertThat(fromDataAndPage.data()).containsExactly("mapped10", "mapped20");
    }
}
