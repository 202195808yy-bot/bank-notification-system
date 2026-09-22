package com.bank.customer.controller;

import com.bank.customer.dto.ProfileRequest;
import com.bank.customer.security.CurrentUserResolver;
import com.bank.customer.service.CustomerProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 当前登录客户的个人资料。邮箱/手机号就是 SMS、Email 渠道的实际收件地址，
 * 客户必须能自助维护，否则通知永远发不出去（修复前连查看入口都没有）。
 */
@Slf4j
@RestController
@RequestMapping("/api/customers/me")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerProfileService profileService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<?> me(HttpServletRequest request) {
        Long customerId = currentUserResolver.resolve(request);
        if (customerId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(profileService.get(customerId));
    }

    @PatchMapping
    public ResponseEntity<?> updateMe(@Valid @RequestBody ProfileRequest body,
                                      HttpServletRequest request) {
        Long customerId = currentUserResolver.resolve(request);
        if (customerId == null) {
            return unauthorized();
        }
        try {
            return ResponseEntity.ok(profileService.updateProfile(customerId, body));
        } catch (CustomerProfileService.EmailAlreadyUsedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("code", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("code", e.getMessage()));
        }
    }

    private ResponseEntity<Object> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", "UNAUTHORIZED"));
    }
}
