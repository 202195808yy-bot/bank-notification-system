package com.bank.customer.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Name is required")
    private String name;
    @NotBlank(message = "Email is required")
    @Email
    private String email;
    @NotBlank(message = "Phone is required")
    private String phone;
    @NotBlank(message = "Password is required")
    private String password;
}