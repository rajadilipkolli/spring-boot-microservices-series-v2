package com.example.retailstore.webapp.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RegistrationViewController {

    @GetMapping("/registration")
    public String showRegistrationPage() {
        return "registration";
    }
}
