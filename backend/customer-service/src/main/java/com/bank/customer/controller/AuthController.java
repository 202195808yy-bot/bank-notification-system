package com.bank.customer.controller;

import com.bank.customer.dto.AuthResponse;
import com.bank.customer.dto.LoginRequest;
import com.bank.customer.dto.RegisterRequest;
import com.bank.customer.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口：注册 / 登录
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            var customer = authService.register(request);
            String token = authService.createToken(customer);
            return ResponseEntity.ok(AuthResponse.of(token, customer));
        } catch (IllegalArgumentException e) {
            // 与全站一致：只回 {code}，人话由前端按界面语言渲染（后端不知道也不该猜用户看什么语言）
            return ResponseEntity.status(HttpStatus.CONFLICT).body(java.util.Map.of("code", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            var customer = authService.login(request);
            String token = authService.createToken(customer);
            log.info("用户登录成功: {}", request.getEmail());
            return ResponseEntity.ok(AuthResponse.of(token, customer));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(java.util.Map.of("code", e.getMessage()));
        }
    }
}
