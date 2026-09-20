package com.bank.customer.dto;

import com.bank.common.constant.AppConstants;
import com.bank.common.entity.Customer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证成功响应：JWT 与用户基本信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;
    private String name;
    private String role;

    public static AuthResponse of(String token, Customer customer) {
        String role = customer.getRole() != null ? customer.getRole() : AppConstants.DEFAULT_ROLE;
        return new AuthResponse(token, customer.getName(), role);
    }
}
