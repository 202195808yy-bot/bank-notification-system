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
            throw new IllegalArgumentException("EMAIL_ALREADY_USED");
        }
        Customer customer = new Customer();
        customer.setName(request.getName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        customer.setPassword(passwordEncoder.encode(request.getPassword()));
        customer.setRole(AppConstants.DEFAULT_ROLE);
        // 注册时把浏览器报告的时区存下来（前端采集 Intl 的结果）：免打扰时段要按客户的钟点判定
        customer.setTimezone(com.bank.customer.util.Timezones.normalize(request.getTimezone()));
        // 正文语言同理采一次（PRD-56）：认不出来就留 NULL，让"未设置"仍然是个可表达的状态，
        // 派发端那套"null → 回退默认语言"的兜底才有意义
        customer.setLocale(com.bank.customer.util.Locales.fromBrowser(request.getLocale()));
        return customerRepository.save(customer);
    }

    /**
     * 登录校验，成功返回客户实体（密码字段不会外泄）
     */
    public Customer login(LoginRequest request) {
        Customer customer = customerRepository.findByEmail(request.getEmail()).orElse(null);
        if (customer == null || !passwordEncoder.matches(request.getPassword(), customer.getPassword())) {
            // 口令错与账号不存在给同一个码：分开就会变成"这个邮箱注册过吗"的探测口
            throw new org.springframework.security.authentication.BadCredentialsException("INVALID_CREDENTIALS");
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
