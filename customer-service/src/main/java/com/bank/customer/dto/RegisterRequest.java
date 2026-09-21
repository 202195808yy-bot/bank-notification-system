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
    /** 浏览器报告的 IANA 时区（Intl.DateTimeFormat().resolvedOptions().timeZone）；可不传，免打扰时段按它判定 */
    private String timezone;
}