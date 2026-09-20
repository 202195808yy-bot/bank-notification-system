package com.bank.customer.service;

import com.bank.common.constant.AppConstants;
import com.bank.common.entity.Customer;
import com.bank.customer.dto.LoginRequest;
import com.bank.customer.dto.RegisterRequest;
import com.bank.customer.repository.CustomerRepository;
import com.bank.customer.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务：注册与登录，返回登录凭证信息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    /**
     * 注册新用户并直接返回 token
     */
    @Transactional
    public Customer register(RegisterRequest request) {
        if (customerRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("该邮箱已注册");
        }
        Customer customer = new Customer();
        customer.setName(request.getName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        customer.setPassword(passwordEncoder.encode(request.getPassword()));
        customer.setRole(AppConstants.DEFAULT_ROLE);
        return customerRepository.save(customer);
    }

    /**
     * 登录校验，成功返回客户实体（密码字段不会外泄）
     */
    public Customer login(LoginRequest request) {
        Customer customer = customerRepository.findByEmail(request.getEmail()).orElse(null);
        if (customer == null || !passwordEncoder.matches(request.getPassword(), customer.getPassword())) {
            throw new org.springframework.security.authentication.BadCredentialsException("邮箱或密码错误");
        }
        return customer;
    }

    /**
     * 为客户生成 JWT
     */
    public String createToken(Customer customer) {
        String role = customer.getRole() != null ? customer.getRole() : AppConstants.DEFAULT_ROLE;
        return tokenProvider.createToken(customer.getId(), role);
    }
}
