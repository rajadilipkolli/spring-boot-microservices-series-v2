package com.example.retailstore.webapp.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.example.retailstore.webapp.common.AbstractIntegrationTest;
import com.example.retailstore.webapp.model.CartItem;
import com.example.retailstore.webapp.model.CartState;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class CartControllerIT extends AbstractIntegrationTest {

    @Test
    void cartDataShouldBeStoredInSession() {
        MockHttpSession session = new MockHttpSession();

        CartItem item = new CartItem("PROD-1", "Product 1", 10.0, 1);
        CartState cart = new CartState(List.of(item), 10.0);
        String requestJson = jsonMapper.writeValueAsString(cart);

        // 1. Add item to cart
        mockMvcTester
                .post()
                .uri("/api/cart")
                .session(session)
                .with(csrf())
                .with(user("user"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
                .assertThat()
                .hasStatus(HttpStatus.OK);

        // 2. Fetch cart to verify it is in session
        mockMvcTester
                .get()
                .uri("/api/cart")
                .session(session)
                .with(user("user"))
                .assertThat()
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .convertTo(CartState.class)
                .satisfies(cartState -> {
                    assertThat(cartState).isNotNull();
                    assertThat(cartState.items()).hasSize(1);
                    assertThat(cartState.items().getFirst().productCode()).isEqualTo("PROD-1");
                    assertThat(cartState.items().getFirst().quantity()).isEqualTo(1);
                });
    }
}
