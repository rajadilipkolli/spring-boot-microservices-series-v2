/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.orderservice;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.orderservice.common.AbstractIntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class OrderServiceApplicationIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shouldFetchAllOrdersFromStream() {
        // waiting till is kafka stream is changed from PARTITIONS_ASSIGNED to RUNNING
        await().pollDelay(5, SECONDS)
                .atMost(15, SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(
                        () ->
                                this.mockMvc
                                        .perform(get("/api/orders/store"))
                                        .andExpect(status().isOk())
                                        .andExpect(
                                                content().contentType(MediaType.APPLICATION_JSON)));
    }
}
