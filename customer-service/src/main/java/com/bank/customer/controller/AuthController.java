package com.bank.customer.controller;

import com.bank.common.entity.Customer;
import com.bank.customer.repository.CustomerRepository;
import com.bank.customer.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtTokenProvider tokenProvider;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Customer customer) {
        if (customerRepository.findByEmail(customer.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "该邮箱已注册"));
        }
        customer.setPassword(passwordEncoder.encode(customer.getPassword()));
        customer.setRole("USER");   // 强制普通用户角色
        customerRepository.save(customer);
        String token = tokenProvider.createToken(customer.getId(), "USER");
        return ResponseEntity.ok(Map.of(
                "token", token,
                "name", customer.getName(),
                "role", "USER"
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginData) {
        String email = loginData.get("email");
        String password = loginData.get("password");
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!passwordEncoder.matches(password, customer.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid password"));
        }
        String token = tokenProvider.createToken(customer.getId(), customer.getRole());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "name", customer.getName(),
                "role", customer.getRole()  // 返回角色
        ));
    }
}