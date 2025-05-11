package com.example.retailstore.webapp.web.model.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
        @NotBlank @Pattern(
                        regexp = "^[a-zA-Z0-9._-]{3,20}$",
                        message =
                                "Username must be 3-20 characters and contain only letters, numbers, dots, underscores or hyphens")
                String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 1, max = 50) String firstName,
        @NotBlank @Size(min = 1, max = 50) String lastName,
        @NotBlank @Pattern(
                        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$",
                        message =
                                "Password must be at least 8 characters and include uppercase, lowercase, numbers and special characters")
                String password) {}
