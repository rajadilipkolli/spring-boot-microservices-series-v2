/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.common.dto.PagedResult;
import com.example.inventoryservice.inventory.InventoryAPI;
import com.example.inventoryservice.inventory.domain.models.InventoryRequest;
import com.example.inventoryservice.inventory.domain.models.InventoryResponse;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = InventoryController.class)
@ActiveProfiles("test")
class InventoryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private InventoryAPI inventoryAPI;

    @Autowired private JsonMapper jsonMapper;

    private List<InventoryResponse> inventoryList;

    /** Builds ten inventory responses used by the controller tests. */
    @BeforeEach
    void setUp() {
        inventoryList =
                IntStream.rangeClosed(1, 10)
                        .mapToObj(i -> new InventoryResponse((long) i, "prod-" + i, 100, 0))
                        .toList();
    }

    /** Verifies default paging returns the API records and pagination metadata. */
    @Test
    void shouldFetchAllInventories() throws Exception {
        PagedResult<InventoryResponse> inventoryPagedResult =
                new PagedResult<>(inventoryList, 10, 1, 1, true, true, false, false);
        given(inventoryAPI.findAllInventories(0, 10, "id", "asc")).willReturn(inventoryPagedResult);

        this.mockMvc
                .perform(get("/api/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size()", is(inventoryList.size())))
                .andExpect(jsonPath("$.totalElements", is(10)))
                .andExpect(jsonPath("$.pageNumber", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.isFirst", is(true)))
                .andExpect(jsonPath("$.isLast", is(true)))
                .andExpect(jsonPath("$.hasNext", is(false)))
                .andExpect(jsonPath("$.hasPrevious", is(false)));
    }

    /** Verifies a known product code returns HTTP 200 and the matching inventory. */
    @Test
    void shouldFindInventoryByProductCode() throws Exception {
        String productCode = "prod-1";
        InventoryResponse inventory = new InventoryResponse(1L, productCode, 100, 0);
        given(inventoryAPI.findInventoryByProductCode(productCode))
                .willReturn(Optional.of(inventory));

        this.mockMvc
                .perform(get("/api/inventory/{productCode}", productCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode", is(inventory.productCode())));
    }

    /** Verifies an absent product code returns HTTP 404. */
    @Test
    void shouldReturn404WhenFetchingNonExistingInventory() throws Exception {
        String productCode = "non-existing";
        given(inventoryAPI.findInventoryByProductCode(productCode)).willReturn(Optional.empty());

        this.mockMvc
                .perform(get("/api/inventory/{productCode}", productCode))
                .andExpect(status().isNotFound());
    }

    /** Verifies a valid creation request returns HTTP 201 and the saved inventory. */
    @Test
    void shouldCreateNewInventory() throws Exception {
        InventoryRequest inventoryRequest = new InventoryRequest("prod-new", 10);
        InventoryResponse inventoryResponse = new InventoryResponse(11L, "prod-new", 10, 0);

        given(inventoryAPI.saveInventory(any(InventoryRequest.class)))
                .willReturn(inventoryResponse);

        this.mockMvc
                .perform(
                        post("/api/inventory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(inventoryRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.productCode", is("prod-new")))
                .andExpect(jsonPath("$.availableQuantity", is(10)));
    }

    @Test
    void shouldReturn400WhenCreateNewInventoryWithoutProductCode() throws Exception {
        InventoryRequest inventoryRequest = new InventoryRequest(null, 0);

        this.mockMvc
                .perform(
                        post("/api/inventory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(inventoryRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(
                        header().string(
                                        "Content-Type",
                                        is(MediaType.APPLICATION_PROBLEM_JSON_VALUE)))
                .andExpect(
                        jsonPath(
                                "$.type",
                                is("https://api.microservices.com/errors/validation-error")))
                .andExpect(jsonPath("$.title", is("Constraint Violation")))
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", is("Invalid request content.")))
                .andExpect(jsonPath("$.instance", is("/api/inventory")));
    }

    @Test
    void shouldReturn400WhenCreateNewInventoryWithNegativeQuantity() throws Exception {
        InventoryRequest bad = new InventoryRequest("p", -1);

        this.mockMvc
                .perform(
                        post("/api/inventory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    /** Verifies updating an absent inventory identifier returns HTTP 404. */
    @Test
    void shouldReturn404WhenUpdatingNonExistingInventory() throws Exception {
        Long inventoryId = 1L;
        InventoryRequest inventoryRequest = new InventoryRequest("product1", 10);
        given(inventoryAPI.updateInventoryById(inventoryId, inventoryRequest))
                .willReturn(Optional.empty());

        this.mockMvc
                .perform(
                        put("/api/inventory/{id}", inventoryId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(inventoryRequest)))
                .andExpect(status().isNotFound());
    }

    /**
     * Verifies an update returns the identifier, product code, and available quantity from the API.
     */
    @Test
    void shouldUpdateInventorySuccessfully() throws Exception {
        Long inventoryId = 5L;
        InventoryRequest req = new InventoryRequest("updated", 22);
        InventoryResponse updated = new InventoryResponse(inventoryId, "updated", 22, 0);

        given(inventoryAPI.updateInventoryById(eq(inventoryId), any(InventoryRequest.class)))
                .willReturn(Optional.of(updated));

        this.mockMvc
                .perform(
                        put("/api/inventory/{id}", inventoryId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(inventoryId.toString())))
                .andExpect(jsonPath("$.productCode", is("updated")))
                .andExpect(jsonPath("$.availableQuantity", is(22)));
    }

    @Test
    void shouldReturn400WhenUpdatingWithInvalidJson() throws Exception {
        String invalidJson = "{\"productCode\": , \"availableQuantity\": }";

        this.mockMvc
                .perform(
                        put("/api/inventory/{id}", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    /** Verifies deleting existing inventory returns HTTP 200 with its previous values. */
    @Test
    void shouldDeleteInventory() throws Exception {
        Long inventoryId = 1L;
        given(inventoryAPI.existsInventoryById(inventoryId)).willReturn(true);
        doNothing().when(inventoryAPI).deleteInventoryById(inventoryId);

        this.mockMvc.perform(delete("/api/inventory/{id}", inventoryId)).andExpect(status().isOk());
    }

    /** Verifies deleting an absent inventory identifier returns HTTP 410. */
    @Test
    void shouldReturn410WhenDeletingNonExistingInventory() throws Exception {
        Long inventoryId = 1L;
        given(inventoryAPI.existsInventoryById(inventoryId)).willReturn(false);

        this.mockMvc
                .perform(delete("/api/inventory/{id}", inventoryId))
                .andExpect(status().isGone());
    }

    /**
     * Verifies generation forwards the idempotency key and an unspecified batch size to the API.
     */
    @Test
    void testUpdateInventoryWithRandomValue() throws Exception {
        doNothing().when(inventoryAPI).updateGeneratedInventory(anyString(), isNull());

        mockMvc.perform(post("/api/inventory/generate").header("Idempotency-Key", "test-batch-123"))
                .andExpect(status().isOk())
                .andExpect(
                        result ->
                                assertThat(
                                                Boolean.parseBoolean(
                                                        result.getResponse().getContentAsString()))
                                        .isTrue());

        verify(inventoryAPI, times(1)).updateGeneratedInventory("test-batch-123", null);
    }

    /** Verifies generation forwards an explicit batch size and returns a successful result. */
    @Test
    void shouldUpdateInventoryWithBatchSize() throws Exception {
        doNothing().when(inventoryAPI).updateGeneratedInventory(anyString(), eq(25));

        mockMvc.perform(
                        post("/api/inventory/generate?batchSize=25")
                                .header("Idempotency-Key", "test-batch-123"))
                .andExpect(status().isOk())
                .andExpect(
                        result ->
                                assertThat(
                                                Boolean.parseBoolean(
                                                        result.getResponse().getContentAsString()))
                                        .isTrue());

        verify(inventoryAPI, times(1)).updateGeneratedInventory("test-batch-123", 25);
    }

    /** Verifies out-of-range batch sizes return HTTP 400 without invoking the inventory API. */
    @ParameterizedTest
    @ValueSource(ints = {0, -1, 10_001})
    void shouldRejectInvalidGenerationBatchSize(int batchSize) throws Exception {
        mockMvc.perform(
                        post("/api/inventory/generate?batchSize=" + batchSize)
                                .header("Idempotency-Key", "test-batch-123"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(inventoryAPI);
    }

    /** Verifies a generation failure from the inventory API produces HTTP 500. */
    @Test
    void shouldReturn500WhenGenerationFails() throws Exception {
        doThrow(new RuntimeException("failure"))
                .when(inventoryAPI)
                .updateGeneratedInventory(anyString(), isNull());

        mockMvc.perform(post("/api/inventory/generate").header("Idempotency-Key", "test-batch-123"))
                .andExpect(status().isInternalServerError());
    }
}
