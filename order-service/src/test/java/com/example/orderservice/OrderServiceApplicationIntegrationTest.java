/* Licensed under Apache-2.0 2021-2023 */
package com.example.orderservice;

import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.orderservice.common.AbstractIntegrationTest;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OrderServiceApplicationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldFetchAllOrdersFromStream() throws Exception {
        // waiting till is kafka stream is changed from PARTITIONS_ASSIGNED to RUNNING
        TimeUnit.SECONDS.sleep(5);
        this.mockMvc
                .perform(get("/api/orders/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()", is(0)));
    }
}
