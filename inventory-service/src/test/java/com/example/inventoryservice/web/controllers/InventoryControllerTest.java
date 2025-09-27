/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.web.controllers;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.inventoryservice.entities.Inventory;
import com.example.inventoryservice.model.request.InventoryRequest;
import com.example.inventoryservice.model.response.PagedResult;
import com.example.inventoryservice.services.InventoryService;
import com.example.inventoryservice.utils.AppConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(InventoryController.class)
@DisplayName("InventoryController Tests")
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @Autowired
    private ObjectMapper objectMapper;

    private Inventory sampleInventory;
    private InventoryRequest sampleInventoryRequest;
    private List<Inventory> inventoryList;
    private PagedResult<Inventory> pagedResult;

    @BeforeEach
    void setUp() {
        // Setup sample data
        sampleInventory = new Inventory();
        sampleInventory.setId(1L);
        sampleInventory.setProductCode("PROD001");
        sampleInventory.setQuantity(100);

        sampleInventoryRequest = new InventoryRequest();
        sampleInventoryRequest.setProductCode("PROD001");
        sampleInventoryRequest.setQuantity(100);

        Inventory secondInventory = new Inventory();
        secondInventory.setId(2L);
        secondInventory.setProductCode("PROD002");
        secondInventory.setQuantity(50);

        inventoryList = Arrays.asList(sampleInventory, secondInventory);

        pagedResult = new PagedResult<>();
        pagedResult.setData(inventoryList);
        pagedResult.setTotalElements(2L);
        pagedResult.setPageNumber(0);
        pagedResult.setTotalPages(1);
        pagedResult.setIsFirst(true);
        pagedResult.setIsLast(true);
        pagedResult.setHasNext(false);
        pagedResult.setHasPrevious(false);
    }

    @Nested
    @DisplayName("GET /api/inventory - Get All Inventories")
    class GetAllInventoriesTests {

        @Test
        @DisplayName("Should return paginated inventories with default parameters")
        void shouldReturnPaginatedInventoriesWithDefaults() throws Exception {
            // given
            given(inventoryService.findAllInventories(0, 10, "id", "asc"))
                    .willReturn(pagedResult);

            // when & then
            mockMvc.perform(get("/api/inventory"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.data", hasSize(2)))
                    .andExpect(jsonPath("$.data[0].id", is(1)))
                    .andExpect(jsonPath("$.data[0].productCode", is("PROD001")))
                    .andExpect(jsonPath("$.data[0].quantity", is(100)))
                    .andExpect(jsonPath("$.data[1].id", is(2)))
                    .andExpect(jsonPath("$.data[1].productCode", is("PROD002")))
                    .andExpect(jsonPath("$.data[1].quantity", is(50)))
                    .andExpect(jsonPath("$.totalElements", is(2)))
                    .andExpect(jsonPath("$.totalPages", is(1)))
                    .andExpect(jsonPath("$.pageNumber", is(0)));

            verify(inventoryService).findAllInventories(0, 10, "id", "asc");
        }

        @ParameterizedTest
        @DisplayName("Should handle various pagination parameters")
        @MethodSource("paginationParameters")
        void shouldHandlePaginationParameters(int pageNo, int pageSize, String sortBy, String sortDir) throws Exception {
            // given
            given(inventoryService.findAllInventories(pageNo, pageSize, sortBy, sortDir))
                    .willReturn(pagedResult);

            // when & then
            mockMvc.perform(get("/api/inventory")
                            .param("pageNo", String.valueOf(pageNo))
                            .param("pageSize", String.valueOf(pageSize))
                            .param("sortBy", sortBy)
                            .param("sortDir", sortDir))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.data", hasSize(2)));

            verify(inventoryService).findAllInventories(pageNo, pageSize, sortBy, sortDir);
        }

        static Stream<Arguments> paginationParameters() {
            return Stream.of(
                    Arguments.of(0, 10, "id", "asc"),
                    Arguments.of(1, 20, "productCode", "desc"),
                    Arguments.of(2, 5, "quantity", "asc"),
                    Arguments.of(0, 50, "id", "desc")
            );
        }

        @Test
        @DisplayName("Should return empty result when no inventories exist")
        void shouldReturnEmptyResultWhenNoInventories() throws Exception {
            // given
            PagedResult<Inventory> emptyResult = new PagedResult<>();
            emptyResult.setData(Collections.emptyList());
            emptyResult.setTotalElements(0L);
            emptyResult.setPageNumber(0);
            emptyResult.setTotalPages(0);

            given(inventoryService.findAllInventories(anyInt(), anyInt(), anyString(), anyString()))
                    .willReturn(emptyResult);

            // when & then
            mockMvc.perform(get("/api/inventory"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements", is(0)));
        }

        @Test
        @DisplayName("Should handle invalid pagination parameters gracefully")
        void shouldHandleInvalidPaginationParameters() throws Exception {
            // given
            given(inventoryService.findAllInventories(anyInt(), anyInt(), anyString(), anyString()))
                    .willReturn(pagedResult);

            // when & then - negative page numbers should be handled by service layer
            mockMvc.perform(get("/api/inventory")
                            .param("pageNo", "-1")
                            .param("pageSize", "0"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/inventory/{productCode} - Get Inventory By Product Code")
    class GetInventoryByProductCodeTests {

        @Test
        @DisplayName("Should return inventory when product code exists")
        void shouldReturnInventoryWhenProductCodeExists() throws Exception {
            // given
            given(inventoryService.findInventoryByProductCode("PROD001"))
                    .willReturn(Optional.of(sampleInventory));

            // when & then
            mockMvc.perform(get("/api/inventory/PROD001"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.productCode", is("PROD001")))
                    .andExpect(jsonPath("$.quantity", is(100)));

            verify(inventoryService).findInventoryByProductCode("PROD001");
        }

        @Test
        @DisplayName("Should return 404 when product code does not exist")
        void shouldReturn404WhenProductCodeNotExists() throws Exception {
            // given
            given(inventoryService.findInventoryByProductCode("NONEXISTENT"))
                    .willReturn(Optional.empty());

            // when & then
            mockMvc.perform(get("/api/inventory/NONEXISTENT"))
                    .andDo(print())
                    .andExpect(status().isNotFound());

            verify(inventoryService).findInventoryByProductCode("NONEXISTENT");
        }

        @Test
        @DisplayName("Should handle delay parameter for slow response simulation")
        void shouldHandleDelayParameter() throws Exception {
            // given
            given(inventoryService.findInventoryByProductCode("PROD001"))
                    .willReturn(Optional.of(sampleInventory));

            // when & then - test with delay (timing is handled by controller, just verify response)
            mockMvc.perform(get("/api/inventory/PROD001")
                            .param("delay", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productCode", is("PROD001")));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  ", "PROD-WITH-SPECIAL-CHARS", "123456", "prod001"})
        @DisplayName("Should handle various product code formats")
        void shouldHandleVariousProductCodeFormats(String productCode) throws Exception {
            // given
            given(inventoryService.findInventoryByProductCode(productCode))
                    .willReturn(Optional.empty());

            // when & then
            mockMvc.perform(get("/api/inventory/" + productCode))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should handle zero and negative delay values")
        void shouldHandleInvalidDelayValues() throws Exception {
            // given
            given(inventoryService.findInventoryByProductCode("PROD001"))
                    .willReturn(Optional.of(sampleInventory));

            // when & then - zero delay
            mockMvc.perform(get("/api/inventory/PROD001")
                            .param("delay", "0"))
                    .andExpect(status().isOk());

            // negative delay
            mockMvc.perform(get("/api/inventory/PROD001")
                            .param("delay", "-1"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/inventory/product - Get Inventory By Product Codes")
    class GetInventoryByProductCodesTests {

        @Test
        @DisplayName("Should return inventories for multiple product codes")
        void shouldReturnInventoriesForMultipleProductCodes() throws Exception {
            // given
            List<String> productCodes = Arrays.asList("PROD001", "PROD002");
            given(inventoryService.getInventoryByProductCodes(productCodes))
                    .willReturn(inventoryList);

            // when & then
            mockMvc.perform(get("/api/inventory/product")
                            .param("codes", "PROD001", "PROD002"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].productCode", is("PROD001")))
                    .andExpect(jsonPath("$[1].productCode", is("PROD002")));

            verify(inventoryService).getInventoryByProductCodes(productCodes);
        }

        @Test
        @DisplayName("Should return empty list when no matching products found")
        void shouldReturnEmptyListWhenNoMatchingProducts() throws Exception {
            // given
            List<String> productCodes = Arrays.asList("NONEXISTENT1", "NONEXISTENT2");
            given(inventoryService.getInventoryByProductCodes(productCodes))
                    .willReturn(Collections.emptyList());

            // when & then
            mockMvc.perform(get("/api/inventory/product")
                            .param("codes", "NONEXISTENT1", "NONEXISTENT2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("Should handle single product code")
        void shouldHandleSingleProductCode() throws Exception {
            // given
            List<String> productCodes = Arrays.asList("PROD001");
            given(inventoryService.getInventoryByProductCodes(productCodes))
                    .willReturn(Arrays.asList(sampleInventory));

            // when & then
            mockMvc.perform(get("/api/inventory/product")
                            .param("codes", "PROD001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].productCode", is("PROD001")));
        }

        @Test
        @DisplayName("Should handle empty product codes list")
        void shouldHandleEmptyProductCodesList() throws Exception {
            // given
            given(inventoryService.getInventoryByProductCodes(Collections.emptyList()))
                    .willReturn(Collections.emptyList());

            // when & then
            mockMvc.perform(get("/api/inventory/product"))
                    .andExpect(status().isBadRequest()); // Spring should validate missing required param
        }
    }

    @Nested
    @DisplayName("GET /api/inventory/generate - Update Inventory With Random Value")
    class UpdateInventoryWithRandomValueTests {

        @Test
        @DisplayName("Should successfully trigger inventory update")
        void shouldSuccessfullyTriggerInventoryUpdate() throws Exception {
            // given
            doNothing().when(inventoryService).updateGeneratedInventory();

            // when & then
            mockMvc.perform(get("/api/inventory/generate"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/json"))
                    .andExpect(content().string("true"));

            verify(inventoryService).updateGeneratedInventory();
        }

        @Test
        @DisplayName("Should handle service exception during inventory update")
        void shouldHandleServiceExceptionDuringUpdate() throws Exception {
            // given
            doThrow(new RuntimeException("Database error"))
                    .when(inventoryService).updateGeneratedInventory();

            // when & then - exception should be propagated (handled by global exception handler)
            mockMvc.perform(get("/api/inventory/generate"))
                    .andExpect(status().isInternalServerError());
        }
    }

    @Nested
    @DisplayName("POST /api/inventory - Create Inventory")
    class CreateInventoryTests {

        @Test
        @DisplayName("Should successfully create inventory with valid request")
        void shouldSuccessfullyCreateInventoryWithValidRequest() throws Exception {
            // given
            given(inventoryService.saveInventory(any(InventoryRequest.class)))
                    .willReturn(sampleInventory);

            // when & then
            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleInventoryRequest)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.productCode", is("PROD001")))
                    .andExpect(jsonPath("$.quantity", is(100)));

            verify(inventoryService).saveInventory(any(InventoryRequest.class));
        }

        @Test
        @DisplayName("Should return 400 for invalid request body")
        void shouldReturn400ForInvalidRequestBody() throws Exception {
            // when & then - empty body
            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest());

            // invalid JSON
            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("invalid json"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when required fields are missing")
        void shouldReturn400WhenRequiredFieldsMissing() throws Exception {
            // given - request with missing required fields
            InventoryRequest invalidRequest = new InventoryRequest();
            // not setting productCode or quantity

            // when & then
            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle service layer exceptions during creation")
        void shouldHandleServiceLayerExceptions() throws Exception {
            // given
            given(inventoryService.saveInventory(any(InventoryRequest.class)))
                    .willThrow(new RuntimeException("Duplicate product code"));

            // when & then
            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleInventoryRequest)))
                    .andExpect(status().isInternalServerError());
        }

        @Test
        @DisplayName("Should handle requests with additional fields gracefully")
        void shouldHandleRequestsWithAdditionalFields() throws Exception {
            // given
            String requestWithExtraFields = """
                {
                    "productCode": "PROD001",
                    "quantity": 100,
                    "extraField": "should be ignored",
                    "anotherField": 123
                }
                """;
            given(inventoryService.saveInventory(any(InventoryRequest.class)))
                    .willReturn(sampleInventory);

            // when & then
            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestWithExtraFields))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("PUT /api/inventory/{id} - Update Inventory")
    class UpdateInventoryTests {

        @Test
        @DisplayName("Should successfully update existing inventory")
        void shouldSuccessfullyUpdateExistingInventory() throws Exception {
            // given
            Long inventoryId = 1L;
            Inventory updatedInventory = new Inventory();
            updatedInventory.setId(inventoryId);
            updatedInventory.setProductCode("PROD001_UPDATED");
            updatedInventory.setQuantity(200);

            given(inventoryService.updateInventoryById(eq(inventoryId), any(InventoryRequest.class)))
                    .willReturn(Optional.of(updatedInventory));

            // when & then
            mockMvc.perform(put("/api/inventory/" + inventoryId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleInventoryRequest)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.productCode", is("PROD001_UPDATED")))
                    .andExpect(jsonPath("$.quantity", is(200)));

            verify(inventoryService).updateInventoryById(eq(inventoryId), any(InventoryRequest.class));
        }

        @Test
        @DisplayName("Should return 404 when inventory to update does not exist")
        void shouldReturn404WhenInventoryToUpdateNotExists() throws Exception {
            // given
            Long nonExistentId = 999L;
            given(inventoryService.updateInventoryById(eq(nonExistentId), any(InventoryRequest.class)))
                    .willReturn(Optional.empty());

            // when & then
            mockMvc.perform(put("/api/inventory/" + nonExistentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleInventoryRequest)))
                    .andExpect(status().isNotFound());

            verify(inventoryService).updateInventoryById(eq(nonExistentId), any(InventoryRequest.class));
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, Long.MAX_VALUE})
        @DisplayName("Should handle edge case ID values")
        void shouldHandleEdgeCaseIdValues(Long id) throws Exception {
            // given
            given(inventoryService.updateInventoryById(eq(id), any(InventoryRequest.class)))
                    .willReturn(Optional.empty());

            // when & then
            mockMvc.perform(put("/api/inventory/" + id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleInventoryRequest)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400 for invalid request body during update")
        void shouldReturn400ForInvalidRequestBodyDuringUpdate() throws Exception {
            // when & then
            mockMvc.perform(put("/api/inventory/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("invalid json"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle string ID parameter gracefully")
        void shouldHandleStringIdParameterGracefully() throws Exception {
            // when & then - invalid ID format should result in 400
            mockMvc.perform(put("/api/inventory/invalid-id")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleInventoryRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/inventory/{id} - Delete Inventory")
    class DeleteInventoryTests {

        @Test
        @DisplayName("Should successfully delete existing inventory")
        void shouldSuccessfullyDeleteExistingInventory() throws Exception {
            // given
            Long inventoryId = 1L;
            given(inventoryService.findInventoryById(inventoryId))
                    .willReturn(Optional.of(sampleInventory));
            doNothing().when(inventoryService).deleteInventoryById(inventoryId);

            // when & then
            mockMvc.perform(delete("/api/inventory/" + inventoryId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.id", is(1)))
                    .andExpect(jsonPath("$.productCode", is("PROD001")))
                    .andExpect(jsonPath("$.quantity", is(100)));

            verify(inventoryService).findInventoryById(inventoryId);
            verify(inventoryService).deleteInventoryById(inventoryId);
        }

        @Test
        @DisplayName("Should return 404 when inventory to delete does not exist")
        void shouldReturn404WhenInventoryToDeleteNotExists() throws Exception {
            // given
            Long nonExistentId = 999L;
            given(inventoryService.findInventoryById(nonExistentId))
                    .willReturn(Optional.empty());

            // when & then
            mockMvc.perform(delete("/api/inventory/" + nonExistentId))
                    .andDo(print())
                    .andExpect(status().isNotFound());

            verify(inventoryService).findInventoryById(nonExistentId);
            verify(inventoryService, never()).deleteInventoryById(anyLong());
        }

        @Test
        @DisplayName("Should handle deletion service exceptions")
        void shouldHandleDeletionServiceExceptions() throws Exception {
            // given
            Long inventoryId = 1L;
            given(inventoryService.findInventoryById(inventoryId))
                    .willReturn(Optional.of(sampleInventory));
            doThrow(new RuntimeException("Cannot delete inventory"))
                    .when(inventoryService).deleteInventoryById(inventoryId);

            // when & then
            mockMvc.perform(delete("/api/inventory/" + inventoryId))
                    .andExpect(status().isInternalServerError());
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, Long.MAX_VALUE})
        @DisplayName("Should handle edge case ID values for deletion")
        void shouldHandleEdgeCaseIdValuesForDeletion(Long id) throws Exception {
            // given
            given(inventoryService.findInventoryById(id))
                    .willReturn(Optional.empty());

            // when & then
            mockMvc.perform(delete("/api/inventory/" + id))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should handle concurrent deletion scenarios")
        void shouldHandleConcurrentDeletionScenarios() throws Exception {
            // given - inventory exists when checked but deleted by another process before deletion
            Long inventoryId = 1L;
            given(inventoryService.findInventoryById(inventoryId))
                    .willReturn(Optional.of(sampleInventory));
            doThrow(new RuntimeException("Entity not found during deletion"))
                    .when(inventoryService).deleteInventoryById(inventoryId);

            // when & then
            mockMvc.perform(delete("/api/inventory/" + inventoryId))
                    .andExpect(status().isInternalServerError());
        }
    }

    @Nested
    @DisplayName("Cross-cutting Concerns")
    class CrossCuttingConcernsTests {

        @Test
        @DisplayName("Should handle requests with unsupported media type")
        void shouldHandleUnsupportedMediaType() throws Exception {
            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<inventory><productCode>PROD001</productCode></inventory>"))
                    .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("Should handle requests with missing Content-Type for POST")
        void shouldHandleRequestsWithMissingContentType() throws Exception {
            mockMvc.perform(post("/api/inventory")
                            .content(objectMapper.writeValueAsString(sampleInventoryRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle HEAD requests to existing endpoints")
        void shouldHandleHeadRequests() throws Exception {
            // given
            given(inventoryService.findInventoryByProductCode("PROD001"))
                    .willReturn(Optional.of(sampleInventory));

            // when & then - HEAD should return headers without body
            mockMvc.perform(head("/api/inventory/PROD001"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(""));
        }

        @Test
        @DisplayName("Should handle OPTIONS requests")
        void shouldHandleOptionsRequests() throws Exception {
            mockMvc.perform(options("/api/inventory"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return 405 for unsupported HTTP methods")
        void shouldReturn405ForUnsupportedHttpMethods() throws Exception {
            mockMvc.perform(patch("/api/inventory/1"))
                    .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("Should handle very long product codes")
        void shouldHandleVeryLongProductCodes() throws Exception {
            // given
            String veryLongProductCode = "PROD" + "A".repeat(1000);
            given(inventoryService.findInventoryByProductCode(veryLongProductCode))
                    .willReturn(Optional.empty());

            // when & then
            mockMvc.perform(get("/api/inventory/" + veryLongProductCode))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should handle requests with special characters in URL")
        void shouldHandleSpecialCharactersInUrl() throws Exception {
            // given
            String productCodeWithSpecialChars = "PROD%20001";
            given(inventoryService.findInventoryByProductCode(anyString()))
                    .willReturn(Optional.empty());

            // when & then
            mockMvc.perform(get("/api/inventory/" + productCodeWithSpecialChars))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Performance and Edge Cases")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class PerformanceAndEdgeCasesTests {

        @Test
        @DisplayName("Should handle large inventory lists")
        void shouldHandleLargeInventoryLists() throws Exception {
            // given - simulate large dataset
            List<Inventory> largeInventoryList = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                Inventory inv = new Inventory();
                inv.setId((long) i);
                inv.setProductCode("PROD" + String.format("%04d", i));
                inv.setQuantity(i * 10);
                largeInventoryList.add(inv);
            }

            PagedResult<Inventory> largePagedResult = new PagedResult<>();
            largePagedResult.setData(largeInventoryList);
            largePagedResult.setTotalElements(1000L);

            given(inventoryService.findAllInventories(anyInt(), anyInt(), anyString(), anyString()))
                    .willReturn(largePagedResult);

            // when & then
            mockMvc.perform(get("/api/inventory")
                            .param("pageSize", "1000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1000)))
                    .andExpect(jsonPath("$.totalElements", is(1000)));
        }

        @Test
        @DisplayName("Should handle requests with extremely large JSON payload")
        void shouldHandleExtremelyLargeJsonPayload() throws Exception {
            // given - create a very large request
            InventoryRequest largeRequest = new InventoryRequest();
            largeRequest.setProductCode("A".repeat(10000)); // Very long product code
            largeRequest.setQuantity(Integer.MAX_VALUE);

            // when & then - this should be handled gracefully by validation or request size limits
            mockMvc.perform(post("/api/inventory")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(largeRequest)))
                    .andExpect(result -> {
                        int status = result.getResponse().getStatus();
                        // Could be 400 (validation error) or 413 (payload too large) or 201 (created)
                        assertTrue(status == 400 || status == 413 || status == 201);
                    });
        }

        @Test
        @DisplayName("Should handle concurrent modification scenarios")
        void shouldHandleConcurrentModificationScenarios() throws Exception {
            // given - simulate concurrent modification
            Long inventoryId = 1L;
            given(inventoryService.findInventoryById(inventoryId))
                    .willReturn(Optional.of(sampleInventory))
                    .willReturn(Optional.empty()); // Second call returns empty (deleted by another thread)

            // when & then - first call succeeds
            mockMvc.perform(delete("/api/inventory/" + inventoryId))
                    .andExpect(status().isOk());

            // Second concurrent call should fail
            mockMvc.perform(delete("/api/inventory/" + inventoryId))
                    .andExpect(status().isNotFound());
        }
    }

    // Helper method for assertions
    private static void assertTrue(boolean condition) {
        if (\!condition) {
            throw new AssertionError("Expected condition to be true");
        }
    }
}
