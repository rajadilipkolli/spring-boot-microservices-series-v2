package com.example.retailstore.webapp.web.controller;

import com.example.retailstore.webapp.clients.customer.CustomerResponse;
import com.example.retailstore.webapp.clients.customer.CustomerServiceClient;
import com.example.retailstore.webapp.services.SecurityHelper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CartViewController {

    private final CustomerServiceClient customerServiceClient;
    private final SecurityHelper securityHelper;

    public CartViewController(CustomerServiceClient customerServiceClient, SecurityHelper securityHelper) {
        this.customerServiceClient = customerServiceClient;
        this.securityHelper = securityHelper;
    }

    @GetMapping("/cart")
    public String cart(Model model) {
        String username = securityHelper.getUsername();
        CustomerResponse customer = customerServiceClient.getCustomerByName(username);
        model.addAttribute("customer", customer);
        return "cart";
    }
}
