/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.web.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.mapper.InventoryMapper;
import com.example.inventoryservice.model.request.InventoryRequest;
import com.example.inventoryservice.model.response.InventoryResponse;
import com.example.inventoryservice.model.response.PagedResult;
import com.example.inventoryservice.services.InventoryService;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.instancio.Instancio;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = InventoryController.class)
@ActiveProfiles("test")
@ExtendWith(InstancioExtension.class)
class InventoryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private InventoryService inventoryService;
    @MockitoBean private InventoryMapper inventoryMapper;

    @Autowired private JsonMapper jsonMapper;

    private List<Inventory> inventoryList;

    @BeforeEach
    void setUp() {
        inventoryList = Instancio.ofList(Inventory.class).size(10).create();
        given(inventoryMapper.toResponse(any(Inventory.class)))
                .willAnswer(
                        invocation -> {
                            Inventory inv = invocation.getArgument(0);
                            return new InventoryResponse(
                                    inv.getId(),
                                    inv.getProductCode(),
                                    inv.getAvailableQuantity(),
                                    inv.getReservedItems());
                        });
    }

    @Test
    void shouldFetchAllInventories() throws Exception {
        List<InventoryResponse> responses =
                inventoryList.stream()
                        .map(
                                inv ->
                                        new InventoryResponse(
                                                inv.getId(),
                                                inv.getProductCode(),
                                                inv.getAvailableQuantity(),
                                                inv.getReservedItems()))
                        .toList();
        Page<InventoryResponse> page = new PageImpl<>(responses);
        PagedResult<InventoryResponse> inventoryPagedResult = new PagedResult<>(page);
        given(inventoryService.findAllInventories(0, 10, "id", "asc"))
                .willReturn(inventoryPagedResult);

        this.mockMvc
                .perform(get("/api/inventory"))
                .andExpect(status().isOk())
                // Root has 8 properties in PagedResult
                .andExpect(jsonPath("$.size()", is(8)))
                .andExpect(jsonPath("$.data.size()", is(inventoryList.size())))
                .andExpect(jsonPath("$.totalElements", is(10)))
                .andExpect(jsonPath("$.pageNumber", is(1)))
                .andExpect(jsonPath("$.totalPages", is(1)))
                .andExpect(jsonPath("$.isFirst", is(true)))
                .andExpect(jsonPath("$.isLast", is(true)))
                .andExpect(jsonPath("$.hasNext", is(false)))
                .andExpect(jsonPath("$.hasPrevious", is(false)));
    }

    @Test
    void shouldFetchAllInventoriesWithCustomParams() throws Exception {
        // Page number is 1-based in PagedResult constructor (page.getNumber() + 1)
        Page<InventoryResponse> page =
                new PageImpl<>(
                        Collections.emptyList(),
                        PageRequest.of(1, 20, Sort.by("productCode").descending()),
                        0);
        PagedResult<InventoryResponse> paged = new PagedResult<>(page);

        given(inventoryService.findAllInventories(1, 20, "productCode", "desc")).willReturn(paged);

        this.mockMvc
                .perform(
                        get("/api/inventory")
                                .param("pageNo", "1")
                                .param("pageSize", "20")
                                .param("sortBy", "productCode")
                                .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size()", is(0)))
                .andExpect(jsonPath("$.pageNumber", is(2)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    void shouldFindInventoryByProductCode() throws Exception {
        String productCode = "product1";
        Inventory inventory =
                new Inventory()
                        .setId(1L)
                        .setProductCode(productCode)
                        .setAvailableQuantity(10)
                        .setReservedItems(0);
        given(inventoryService.findInventoryByProductCode(productCode))
                .willReturn(Optional.of(inventory));

        this.mockMvc
                .perform(get("/api/inventory/{productCode}", productCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode", is(inventory.getProductCode())))
                .andExpect(jsonPath("$.availableQuantity", is(10)));
    }

    @Test
    void shouldReturn404WhenFetchingNonExistingInventoryByProductCode() throws Exception {
        String missingCode = "does-not-exist";
        given(inventoryService.findInventoryByProductCode(missingCode))
                .willReturn(Optional.empty());

        this.mockMvc
                .perform(get("/api/inventory/{productCode}", missingCode))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRespectDelayParameterInGetByProductCode() throws Exception {
        String code = "product-delay";
        Inventory inventory =
                new Inventory()
                        .setId(1L)
                        .setProductCode(code)
                        .setAvailableQuantity(5)
                        .setReservedItems(0);
        given(inventoryService.findInventoryByProductCode(code)).willReturn(Optional.of(inventory));

        long start = System.nanoTime();
        this.mockMvc
                .perform(get("/api/inventory/{productCode}", code).param("delay", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode", is(code)));
        long tookMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(tookMs).isGreaterThanOrEqualTo(900);
    }

    @Test
    void shouldReturnInventoriesByProductCodes() throws Exception {
        InventoryResponse inv1 = new InventoryResponse(1L, "P1", 3, 0);
        InventoryResponse inv2 = new InventoryResponse(2L, "P2", 7, 0);

        Page<InventoryResponse> page = new PageImpl<>(Arrays.asList(inv1, inv2));
        given(
                        inventoryService.getInventoryByProductCodes(
                                Arrays.asList("P1", "P2"), 0, 10, "id", "asc"))
                .willReturn(new PagedResult<>(page));

        this.mockMvc
                .perform(get("/api/inventory/product").param("codes", "P1", "P2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size()", is(2)))
                .andExpect(jsonPath("$.data[0].productCode", is("P1")))
                .andExpect(jsonPath("$.data[1].productCode", is("P2")));
    }

    @Test
    void shouldReturnEmptyListWhenCodesNotFound() throws Exception {
        Page<InventoryResponse> page = new PageImpl<>(Collections.emptyList());
        given(
                        inventoryService.getInventoryByProductCodes(
                                Arrays.asList("X1", "X2"), 0, 10, "id", "asc"))
                .willReturn(new PagedResult<>(page));

        this.mockMvc
                .perform(get("/api/inventory/product").param("codes", "X1", "X2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size()", is(0)));
    }

    @Test
    void shouldReturn400WhenCodesMissing() throws Exception {
        this.mockMvc.perform(get("/api/inventory/product")).andExpect(status().isBadRequest());
    }

    @Test
    void shouldCreateNewInventory() throws Exception {
        Inventory inventory =
                new Inventory()
                        .setId(1L)
                        .setProductCode("product1")
                        .setAvailableQuantity(10)
                        .setReservedItems(0);
        given(inventoryService.saveInventory(any(InventoryRequest.class))).willReturn(inventory);

        InventoryRequest inventoryRequest = new InventoryRequest("product1", 10);
        this.mockMvc
                .perform(
                        post("/api/inventory")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(inventoryRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.productCode", is(inventory.getProductCode())))
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

    @Test
    void shouldReturn404WhenUpdatingNonExistingInventory() throws Exception {
        Long inventoryId = 1L;
        InventoryRequest inventoryRequest = new InventoryRequest("product1", 10);
        given(inventoryService.updateInventoryById(inventoryId, inventoryRequest))
                .willReturn(Optional.empty());

        this.mockMvc
                .perform(
                        put("/api/inventory/{id}", inventoryId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(inventoryRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateInventorySuccessfully() throws Exception {
        Long inventoryId = 5L;
        InventoryRequest req = new InventoryRequest("updated", 22);
        Inventory updated =
                new Inventory()
                        .setId(inventoryId)
                        .setProductCode("updated")
                        .setAvailableQuantity(22)
                        .setReservedItems(0);

        given(inventoryService.updateInventoryById(eq(inventoryId), any(InventoryRequest.class)))
                .willReturn(Optional.of(updated));

        this.mockMvc
                .perform(
                        put("/api/inventory/{id}", inventoryId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(inventoryId.intValue())))
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

    @Test
    void shouldDeleteInventory() throws Exception {
        Long inventoryId = 1L;
        Inventory inventory =
                new Inventory()
                        .setId(1L)
                        .setProductCode("product1")
                        .setAvailableQuantity(10)
                        .setReservedItems(0);
        given(inventoryService.findInventoryById(inventoryId)).willReturn(Optional.of(inventory));
        doNothing().when(inventoryService).deleteInventoryById(inventory.getId());

        this.mockMvc
                .perform(delete("/api/inventory/{id}", inventory.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode", is(inventory.getProductCode())))
                .andExpect(jsonPath("$.availableQuantity", is(10)));
    }

    @Test
    void shouldReturn404WhenDeletingNonExistingInventory() throws Exception {
        Long inventoryId = 1L;
        given(inventoryService.findInventoryById(inventoryId)).willReturn(Optional.empty());

        this.mockMvc
                .perform(delete("/api/inventory/{id}", inventoryId))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateInventoryWithRandomValue() throws Exception {
        // Setup
        doNothing().when(inventoryService).updateGeneratedInventory(any(String.class));

        // Execute & Verify
        mockMvc.perform(post("/api/inventory/generate").header("Idempotency-Key", "test-batch-123"))
                .andExpect(status().isOk())
                .andExpect(
                        result ->
                                assertThat(
                                                Boolean.parseBoolean(
                                                        result.getResponse().getContentAsString()))
                                        .isTrue());

        // Verify interactions
        verify(inventoryService, times(1)).updateGeneratedInventory(any(String.class));
    }

    @Test
    void shouldReturn500WhenGenerationFails() throws Exception {
        doThrow(new RuntimeException("failure"))
                .when(inventoryService)
                .updateGeneratedInventory(any(String.class));

        mockMvc.perform(post("/api/inventory/generate").header("Idempotency-Key", "test-batch-123"))
                .andExpect(status().isInternalServerError());
    }
}
