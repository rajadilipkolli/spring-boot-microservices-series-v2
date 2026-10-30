package com.example.retailstore.webapp.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.example.common.dto.PagedResult;
import com.example.retailstore.webapp.clients.inventory.InventoryResponse;
import com.example.retailstore.webapp.clients.inventory.InventoryServiceClient;
import com.example.retailstore.webapp.config.SecurityConfig;
import com.example.retailstore.webapp.services.SecurityHelper;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(controllers = InventoryController.class)
@Import({SecurityConfig.class, SecurityHelper.class})
class InventoryControllerTest {
    private static final String INVENTORY_PATH = "/inventory";
    private static final String INVENTORY_API_PATH = "/api/inventory";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private InventoryServiceClient inventoryServiceClient;

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToAccessInventoryPage() throws Exception {
        mockMvc.perform(get(INVENTORY_PATH).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("inventory"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldNotAllowUserToAccessInventoryPage() throws Exception {
        mockMvc.perform(get(INVENTORY_PATH).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToGetInventoryData() throws Exception {
        var inventory = new InventoryResponse(1L, "SKU1", 10, 0);

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

        mockMvc.perform(get(INVENTORY_API_PATH).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().json(jsonMapper.writeValueAsString(pagedResult)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10})
    @WithMockUser(roles = "ADMIN")
    void shouldAllowAdminToUpdateInventory(int quantity) throws Exception {
        // Arrange
        var request = new InventoryResponse(1L, "SKU1", quantity, null);
        var response = new InventoryResponse(1L, "SKU1", quantity, null);
        var updateRequest = request.createInventoryUpdateRequest();

        when(inventoryServiceClient.updateInventory(eq(1L), eq(updateRequest))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(put(INVENTORY_PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().json(jsonMapper.writeValueAsString(response)));
    }

    @ParameterizedTest
    @MethodSource("invalidInventoryUpdates")
    @WithMockUser(roles = "ADMIN")
    void shouldRejectInvalidInventoryBeforeClientCall(InventoryResponse inventory) throws Exception {
        mockMvc.perform(put(INVENTORY_PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(inventory)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(inventoryServiceClient);
    }

    private static Stream<InventoryResponse> invalidInventoryUpdates() {
        return Stream.of(
                new InventoryResponse(1L, null, 10, null),
                new InventoryResponse(1L, "", 10, null),
                new InventoryResponse(1L, " \t\n", 10, null),
                new InventoryResponse(1L, "SKU1", null, null),
                new InventoryResponse(1L, "SKU1", -1, null));
    }

    @Test
    @WithMockUser(roles = "USER")
    void shouldNotAllowUserToUpdateInventory() throws Exception {
        var inventory = new InventoryResponse(1L, "SKU1", 10, 0);

        mockMvc.perform(put(INVENTORY_PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(inventory)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRedirectUnauthenticatedUserToLoginPage() throws Exception {
        mockMvc.perform(get(INVENTORY_PATH))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String location = result.getResponse().getHeader("Location");
                    assertThat(location)
                            .isNotNull()
                            .satisfiesAnyOf(
                                    loc -> assertThat(loc).endsWith("/login"),
                                    loc -> assertThat(loc).contains("/oauth2/authorization/"));
                });
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldRejectInventoryUpdateWithoutCsrfToken() throws Exception {
        var inventory = new InventoryResponse(1L, "SKU1", 10, 0);

        mockMvc.perform(put(INVENTORY_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(inventory)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void shouldAllowInventoryUpdateWithCsrfToken() throws Exception {
        var inventory = new InventoryResponse(1L, "SKU1", 10, 0);
        when(inventoryServiceClient.updateInventory(eq(1L), any())).thenReturn(inventory);

        mockMvc.perform(put(INVENTORY_PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(inventory)))
                .andExpect(status().isOk())
                .andExpect(content().json(jsonMapper.writeValueAsString(inventory)));
    }
}
