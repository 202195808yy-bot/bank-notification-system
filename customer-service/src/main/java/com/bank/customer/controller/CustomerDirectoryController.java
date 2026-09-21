package com.bank.customer.controller;

import com.bank.common.constant.AppConstants;
import com.bank.customer.dto.CustomerDirectoryEntry;
import com.bank.customer.service.CustomerProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员用户目录：事件发送页要按人挑选目标用户，历史页要把 customerId 翻成可读标识。
 * 这两处在修复前都只能显示裸 id（甚至前端硬编码 id），运维无法判断"这是谁"。
 */
@Slf4j
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerDirectoryController {

    private final CustomerProfileService profileService;

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!AppConstants.ROLE_ADMIN.equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("code", "ADMIN_REQUIRED"));
        }
        return ResponseEntity.ok(profileService.directory());
    }
}
