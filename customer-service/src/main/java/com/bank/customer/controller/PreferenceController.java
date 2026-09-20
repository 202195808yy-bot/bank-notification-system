package com.bank.customer.controller;

import com.bank.common.entity.NotificationPreference;
import com.bank.customer.security.CurrentUserResolver;
import com.bank.customer.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferenceService preferenceService;
    private final CurrentUserResolver currentUserResolver;

    /**
     * 查询当前用户的通知偏好
     */
    @GetMapping
    public ResponseEntity<?> getPreferences(HttpServletRequest request) {
        Long customerId = requireCustomerId(request);
        if (customerId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(preferenceService.getPreferences(customerId));
    }

    /**
     * 全量更新当前用户的通知偏好
     */
    @PutMapping
    public ResponseEntity<?> updatePreferences(@RequestBody List<NotificationPreference> preferences,
                                               HttpServletRequest request) {
        Long customerId = requireCustomerId(request);
        if (customerId == null) {
            return unauthorized();
        }
        try {
            preferenceService.updatePreferences(customerId, preferences);
            return ResponseEntity.ok().build();
        } catch (DataIntegrityViolationException e) {
            // 唯一键冲突（同一 (customer_id, event_type) 重复）
            log.warn("偏好更新冲突: customerId={}", customerId, e);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "数据冲突：同一事件类型的偏好重复"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private Long requireCustomerId(HttpServletRequest request) {
        return currentUserResolver.resolve(request);
    }

    private ResponseEntity<Object> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "未认证"));
    }
}
