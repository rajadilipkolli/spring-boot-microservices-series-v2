package com.example.retailstore.webapp.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.retailstore.webapp.clients.PagedResult;
import com.example.retailstore.webapp.clients.inventory.InventoryResponse;
import com.example.retailstore.webapp.clients.inventory.InventoryServiceClient;
import com.example.retailstore.webapp.config.TestSecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InventoryController.class)
@Import(TestSecurityConfig.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private InventoryServiceClient inventoryServiceClient;

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToAccessInventoryPage() throws Exception {
        mockMvc.perform(get("/inventory").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("inventory"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldNotAllowUserToAccessInventoryPage() throws Exception {
        mockMvc.perform(get("/inventory").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToGetInventoryData() throws Exception {
        var inventory = new InventoryResponse(1L, "SKU1", 10, null);

        var pagedResult = new PagedResult<>(
                List.of(inventory),
                1L, // totalElements
                0, // pageNumber
                1, // totalPages
                true, // isFirst
                true, // isLast
                false, // hasNext
                false // hasPrevious
                );

        when(inventoryServiceClient.getInventories(eq(0))).thenReturn(pagedResult);

        mockMvc.perform(get("/api/inventory").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(pagedResult)));
        ;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToUpdateInventory() throws Exception {
        var inventory = new InventoryResponse(1L, "SKU1", 10, null);
        when(inventoryServiceClient.updateInventory(eq(1L), any())).thenReturn(inventory);

        mockMvc.perform(put("/inventory")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inventory)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldNotAllowUserToUpdateInventory() throws Exception {
        var inventory = new InventoryResponse(1L, "SKU1", 10, null);

        mockMvc.perform(put("/inventory")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inventory)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRedirectUnauthenticatedUserToLoginPage() throws Exception {
        mockMvc.perform(get("/inventory").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
