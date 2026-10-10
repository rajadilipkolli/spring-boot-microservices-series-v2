/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.inventoryservice.inventory.common.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class InventoryServiceApplicationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void contextLoads() throws Exception {
        this.mockMvc.perform(get("/actuator")).andExpect(status().isOk());
    }
}
